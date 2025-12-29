package archon.raft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.PriorityQueue

data class Event(
    val receiver: Int,
    val payload: EventPayload,
    val arrivalTime: Long,
) : Comparable<Event> {
    override fun compareTo(other: Event) =
        this.arrivalTime.compareTo(other.arrivalTime)
}

class SimulationContext(
    private val sender: Int,
    private val queue: PriorityQueue<Event>,
    private val random: kotlin.random.Random,
) : Context {
    var internalClock: Long = 0
        set(value) {
            require(value >= field) { "Clock decreasing: from $field to $value" }
            field = value
        }

    override fun now(): Long = this.internalClock

    override fun send(receiver: Int, message: Message) {
        val latency = this.random.nextInt(5, 50)
        val payload = EventPayload.Network(this.sender, message)

        this.queue.add(Event(receiver, payload, this.internalClock + latency))
    }

    override fun schedule(timer: Timer, delay: Long) {
        val payload = EventPayload.Timeout(timer)

        this.queue.add(Event(this.sender, payload, this.internalClock + delay))
    }

    override fun random(range: IntRange): Int =
        this.random.nextInt(range.first, range.last + 1)
}

class Simulator(val seed: Long) {
    private val random = kotlin.random.Random(seed)
    val queue = PriorityQueue<Event>()
    val nodes = mutableMapOf<Int, Node>()

    fun registerNode(id: Int, nodeFactory: (Context) -> Node) {
        val context = SimulationContext(id, this.queue, this.random)
        val node = nodeFactory(context)
        this.nodes[id] = node
    }

    fun run() {
        while (this.queue.isNotEmpty()) {
            val (receiver, payload, arrivalTime) = this.queue.poll()!!

            val node = this.nodes[receiver]
            if (node == null) throw IllegalStateException("Message sent to unknown node: $receiver")

            if (node !is RaftNode) {
                throw IllegalStateException("Trying to run simulation on a non-raft node: $node")
            } else if (node.context !is SimulationContext) {
                throw IllegalStateException("Trying to run simulation on a node without a simulation context: $node")
            }

            node.context.internalClock = arrivalTime
            node.onEvent(payload)
        }
    }
}

class LibraryTest {
    @Test
    fun foobar() {
        val simulator = Simulator(42)

        simulator.registerNode(0) { RaftNode(it) }
        simulator.registerNode(1) { RaftNode(it) }

        simulator.queue.add(Event(0, EventPayload.Network(1, Message.Ping()), 100))
        simulator.queue.add(Event(1, EventPayload.Network(0, Message.Ping()), 100))

        simulator.run()
    }
}
