package archon.raft

import archon.moirai.Event
import archon.moirai.EventPayload
import archon.moirai.SimulationContext
import archon.moirai.Simulator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryTest {
    @Test
    fun foobar() {
        val simulator = Simulator<Timer, Message>(42)

        val nodesCount = 3
        val quorumSize = (nodesCount / 2) + 1

        for (i in 1..nodesCount) {
            simulator.registerNode { nodeid, context ->
                RaftNode<SimulationContext<Timer, Message>>(context, nodeid, quorumSize)
            }
        }

        simulator.dumpState()

        simulator.init()

        for (i in 0..100) simulator.run()
    }
}
