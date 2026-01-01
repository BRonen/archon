package archon.raft

import archon.moirai.Node
import archon.moirai.Context
import archon.moirai.EventPayload

sealed class Timer() {
    data class Custom(val id: String) : Timer()
}

sealed class Message() {
    class Ping() : Message()

    class Pong() : Message()
}

class RaftNode<C : Context<Timer, Message>>(
    override val context: C,
) : Node<Timer, Message, C> {
    private fun onNetwork(event: EventPayload.Network<Timer, Message>) {
        when (event.message) {
            is Message.Ping -> this.context.send(event.sender, Message.Pong())
            is Message.Pong -> println("pong received from ${event.sender}")
        }
    }

    private fun onTimeout(event: EventPayload.Timeout<Timer, Message>) {
        when (val timer = event.timer) {
            is Timer.Custom -> println("timer ${timer.id} reached")
        }
    }

    override fun onEvent(event: EventPayload<Timer, Message>) {
        when (event) {
            is EventPayload.Network -> this.onNetwork(event)
            is EventPayload.Timeout -> this.onTimeout(event)
        }
    }
}
