package archon.raft

import archon.moirai.Context
import archon.moirai.EventPayload
import archon.moirai.Node

sealed class Option<out T> {
    data class Some<out T>(val value: T) : Option<T>()

    data object None : Option<Nothing>()
}

sealed class Role {
    data object Leader : Role()

    data object Candidate : Role()

    data object Follower : Role()
}

sealed class Timer {
    data class ElectionsTrigger(val term: Int) : Timer()
}

sealed class Message {
    data class VoteRequest(val term: Int) : Message()

    data class VoteResponse(val term: Int) : Message()

    data class AppendEntries(val term: Int) : Message()
}

class RaftNode<C : Context<Timer, Message>>(
    override var context: C,
    override val nodeid: Int,
    val quorumSize: Int,
) : Node<Timer, Message, C> {
    var term: Int = 0
    var role: Role = Role.Follower

    var votesReceived: Int = 0
    var votedFor: Option<Int> = Option.None

    var leaderHeard: Boolean = false

    private fun onNetwork(event: EventPayload.Network<Timer, Message>) {
        when (val message = event.message) {
            is Message.VoteRequest -> {
                if (message.term < this.term) return

                if (message.term > this.term) {
                    this.term = message.term
                    this.role = Role.Follower
                    this.votedFor = Option.Some(event.sender)

                    return context.send(event.sender, Message.VoteResponse(this.term))
                }

                if (this.role != Role.Follower) return

                this.term = message.term
                this.votedFor = Option.Some(event.sender)

                return context.send(event.sender, Message.VoteResponse(this.term))
            }
            is Message.VoteResponse -> {
                if (message.term < this.term) return
                if (this.role != Role.Candidate) return

                this.votesReceived += 1
                if (votesReceived < this.quorumSize) return

                this.role = Role.Leader

                context.broadcast(Message.AppendEntries(this.term))
            }
            is Message.AppendEntries -> {
                if (message.term < this.term) return

                this.leaderHeard = true
                this.role = Role.Follower
            }
        }
    }

    private fun onTimeout(event: EventPayload.Timeout<Timer, Message>) {
        when (val timer = event.timer) {
            is Timer.ElectionsTrigger -> {
                if (this.term < timer.term) throw RuntimeException("Timeout dispatched in an invalid future term")
                if (this.term > timer.term) return

                this.term += 1
                this.role = Role.Candidate
                this.votesReceived = 1
                this.votedFor = Option.Some(this.nodeid)
                context.broadcast(Message.VoteRequest(this.term))

                val delay = context.random(50..100).toLong()
                context.schedule(Timer.ElectionsTrigger(this.term), delay)
            }
        }
    }

    override fun onEvent(event: EventPayload<Timer, Message>) {
        when (event) {
            is EventPayload.Network -> this.onNetwork(event)
            is EventPayload.Timeout -> this.onTimeout(event)
        }
    }

    override fun onInit() {
        if (this.role != Role.Follower || this.leaderHeard) return

        val delay = context.random(50..100).toLong()
        context.schedule(Timer.ElectionsTrigger(this.term), delay)
    }

    override fun dumpState() {
        this.context.dumpState()
        println("  term: ${this.term}")
        println("  role: ${this.role}")
        println("  votesReceived: ${this.votesReceived}")
        println("  votedFor: ${this.votedFor}")
        println("  leaderHeard: ${this.leaderHeard}")
    }
}
