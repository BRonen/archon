package archon.moirai

import java.util.PriorityQueue

sealed class EventPayload<T, M> {
    data class Network<T, M>(val sender: Int, val message: M) : EventPayload<T, M>()

    data class Timeout<T, M>(val timer: T) : EventPayload<T, M>()
}

interface Context<T, M> {
    fun dumpState()

    fun now(): Long

    fun send(receiver: Int, message: M)

    fun broadcast(message: M)

    fun schedule(timer: T, delay: Long)

    fun random(range: IntRange): Int
}

interface Node<T, M, C : Context<T, M>> {
    val context: C

    val nodeid: Int

    fun dumpState()

    fun onInit()

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
    private val nodes: Map<Int, Node<T, M, SimulationContext<T, M>>>,
) : Context<T, M> {
    var internalClock: Long = 0
        set(value) {
            require(value >= field) { "[Moirai] clock decreasing: from $field to $value" }
            field = value
        }

    override fun dumpState() {
        println("  context: {")
        println("    internal clock: ${this.internalClock}")
        println("  }")
    }

    override fun now(): Long {
        println("[Moirai] ${this.sender} -> now()")
        return this.internalClock
    }

    override fun send(receiver: Int, message: M) {
        println("[Moirai] ${this.sender} -> send($receiver, $message)")
        val latency = this.random.nextInt(5, 50)
        val payload = EventPayload.Network<T, M>(this.sender, message)

        require(this.internalClock + latency >= this.internalClock) { "[Moirai] error invalid send" }

        this.queue.add(Event<T, M>(receiver, payload, this.internalClock + latency))
    }

    override fun broadcast(message: M) {
        println("[Moirai] ${this.sender} -> broadcast($message)")
        for (receiver in this.nodes.keys) {
            if (this.sender == receiver) continue

            this.send(receiver, message)
        }
    }

    override fun schedule(timer: T, delay: Long) {
        println("[Moirai] ${this.sender} -> schedule($timer, $delay)")
        val payload = EventPayload.Timeout<T, M>(timer)

        this.queue.add(Event<T, M>(this.sender, payload, this.internalClock + delay))
    }

    override fun random(range: IntRange): Int {
        println("[Moirai] ${this.sender} -> random($range)")
        return this.random.nextInt(range.first, range.last + 1)
    }
}

class Simulator<T, M>(
    val seed: Long,
) {
    private val random = kotlin.random.Random(seed)
    val queue = PriorityQueue<Event<T, M>>()
    val nodes = mutableMapOf<Int, Node<T, M, SimulationContext<T, M>>>()

    fun dumpState() {
        println("queue: ${this.queue}")
        for (nodeid in 0..<nodes.size) {
            println("node[$nodeid]:")
            val node = this.nodes[nodeid]
            if (node == null) throw IllegalStateException("Selected unknown node: $nodeid")

            node.dumpState()
        }
    }

    fun registerNode(nodeFactory: (Int, SimulationContext<T, M>) -> Node<T, M, SimulationContext<T, M>>) {
        val nodeid = this.nodes.size

        val context = SimulationContext(nodeid, this.queue, this.random, this.nodes.toMap())
        val node = nodeFactory(nodeid, context)

        this.nodes[nodeid] = node
    }

    fun init() {
        for (nodeid in 0..<this.nodes.size) {
            println("[Moirai] running onInit of node $nodeid")

            val node = this.nodes[nodeid]
            if (node == null) throw IllegalStateException("Selected unknown node: $nodeid")

            node.onInit()
        }
    }

    fun run() {
        if (this.queue.isEmpty()) return

        val (receiver, payload, arrivalTime) = this.queue.poll()!!
        when (payload) {
            is EventPayload.Network -> println("[Moirai] sending network message from ${payload.sender} to $receiver : $payload")
            is EventPayload.Timeout -> println("[Moirai] timeout of node $receiver reached : $payload")
        }

        val node = this.nodes[receiver]
        if (node == null) throw IllegalStateException("Event sent to unknown node: $receiver")

        node.context.internalClock = arrivalTime
        node.onEvent(payload)
        this.dumpState()
    }
}
