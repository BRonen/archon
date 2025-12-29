package archon.raft

sealed class Timer {
    // will have more Timers here...

    data class Custom(val id: String) : Timer()
}

sealed class EventPayload {
    data class Network(val sender: Int, val message: Message) : EventPayload()

    data class Timeout(val timer: Timer) : EventPayload()
}

sealed class Message() {
    class Ping() : Message()

    class Pong() : Message()
}

interface Context {
    fun now(): Long

    fun send(receiver: Int, message: Message)

    fun schedule(timer: Timer, delay: Long)

    fun random(range: IntRange): Int
}

interface Node {
    fun onEvent(event: EventPayload)
}

class RaftNode(
    val context: Context,
) : Node {
    private fun onNetwork(event: EventPayload.Network) {
        when (event.message) {
            is Message.Ping -> this.context.send(event.sender, Message.Pong())
            is Message.Pong -> println("pong received from ${event.sender}")
        }
    }

    private fun onTimeout(event: EventPayload.Timeout) {
        when (event.timer) {
            is Timer.Custom -> println("event ${event.timer.id} received")
        }
    }

    override fun onEvent(event: EventPayload) {
        when (event) {
            is EventPayload.Network -> this.onNetwork(event)
            is EventPayload.Timeout -> this.onTimeout(event)
        }
    }
}
