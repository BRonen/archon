package archon.raft

import archon.moirai.Context
import archon.moirai.Event
import archon.moirai.EventPayload
import archon.moirai.SimulationContext
import archon.moirai.Simulator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RaftInvariants {
    fun check(simulator: Simulator<Timer, Message>) {
        // Raft can have only one Leader per Term
        val nodes = simulator.nodes.values.filterIsInstance<RaftNode<Context<Timer, Message>>>()
        val leadersByTerm = (nodes.filter { node -> node.role == Role.Leader }).groupBy { node -> node.term }

        for ((term, leaders) in leadersByTerm) {
            require(leaders.size <= 1) { "[Invariants] more than one leader at same time on term $term: ${leaders.size} found" }
        }
    }
}

class LibraryTest {
    @Test
    fun foobar() {
        val simulator = Simulator<Timer, Message>(128736182736)
        val invariants = RaftInvariants()

        val nodesCount = 3
        val quorumSize = (nodesCount / 2) + 1

        for (i in 1..nodesCount) {
            simulator.registerNode { nodeid, context ->
                RaftNode<SimulationContext<Timer, Message>>(context, nodeid, quorumSize)
            }
        }

        simulator.dumpState()
        simulator.init()
        invariants.check(simulator)

        for (i in 0..100) {
            simulator.run()
            invariants.check(simulator)
        }
    }

    @Test
    fun electionImpasse() {
        val simulator = Simulator<Timer, Message>(42)
        val invariants = RaftInvariants()

        val nodesCount = 3
        val quorumSize = (nodesCount / 2) + 1

        for (i in 1..nodesCount) {
            simulator.registerNode { nodeid, context ->
                RaftNode<SimulationContext<Timer, Message>>(context, nodeid, quorumSize)
            }
        }

        for (nodeid in 0..<nodesCount) {
            val node = simulator.nodes[nodeid]
            require(node is RaftNode) { "invalid node found" }

            node.term = 1
            node.role = Role.Candidate
            node.votesReceived = 1
            node.votedFor = Option.Some(nodeid)

            val delay = node.context.random(50..100).toLong()
            node.context.schedule(Timer.ElectionsTrigger(node.term, 0), delay)
        }

        simulator.dumpState()
        invariants.check(simulator)

        for (i in 0..100) {
            simulator.run()
            invariants.check(simulator)
        }
    }
}
