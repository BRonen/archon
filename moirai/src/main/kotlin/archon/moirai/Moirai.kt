package archon.moirai

import java.util.PriorityQueue

sealed class EventPayload<T, M> {
    data class Network<T, M>(val sender: Int, val message: M) : EventPayload<T, M>()

    data class Timeout<T, M>(val timer: T) : EventPayload<T, M>()
}

interface Context<T, M> {
    fun now(): Long

    fun send(receiver: Int, message: M)

    fun schedule(timer: T, delay: Long)

    fun random(range: IntRange): Int
}

interface Node<T, M, C : Context<T, M>> {
    val context: C

    fun onEvent(event: EventPayload<T, M>)
}

data class Event<T, M>(
    val receiver: Int,
    val payload: EventPayload<T, M>,
    val arrivalTime: Long,
) : Comparable<Event<T, M>> {
    override fun compareTo(other: Event<T, M>) =
        this.arrivalTime.compareTo(other.arrivalTime)
}

class SimulationContext<T, M>(
    private val sender: Int,
    private val queue: PriorityQueue<Event<T, M>>,
    private val random: kotlin.random.Random,
) : Context<T, M> {
    var internalClock: Long = 0
        set(value) {
            require(value >= field) { "Clock decreasing: from $field to $value" }
            field = value
        }

    override fun now(): Long = this.internalClock

    override fun send(receiver: Int, message: M) {
        val latency = this.random.nextInt(5, 50)
        val payload = EventPayload.Network<T, M>(this.sender, message)

        this.queue.add(Event<T, M>(receiver, payload, this.internalClock + latency))
    }

    override fun schedule(timer: T, delay: Long) {
        val payload = EventPayload.Timeout<T, M>(timer)

        this.queue.add(Event<T, M>(this.sender, payload, this.internalClock + delay))
    }

    override fun random(range: IntRange): Int =
        this.random.nextInt(range.first, range.last + 1)
}

class Simulator<T, M>(val seed: Long) {
    private val random = kotlin.random.Random(seed)
    val queue = PriorityQueue<Event<T, M>>()
    val nodes = mutableMapOf<Int, Node<T, M, SimulationContext<T, M>>>()

    fun registerNode(id: Int, nodeFactory: (SimulationContext<T, M>) -> Node<T, M, SimulationContext<T, M>>) {
        val context = SimulationContext(id, this.queue, this.random)
        val node = nodeFactory(context)
        this.nodes[id] = node
    }

    fun run() {
        while (this.queue.isNotEmpty()) {
            val (receiver, payload, arrivalTime) = this.queue.poll()!!

            val node = this.nodes[receiver]
            if (node == null) throw IllegalStateException("Event sent to unknown node: $receiver")

            node.context.internalClock = arrivalTime
            node.onEvent(payload)
        }
    }
}
