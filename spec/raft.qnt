module raft {
  type Node = int
  type Term = int

  type Role =
    | Follower
    | Candidate
    | Leader

  type MaybeVote =
    | Vote({ node: Node })
    | None

  type Message =
    | RequestVote({ term: Term, candidateId: Node })
    | VoteResponse({ term: Term, granted: bool, voterId: Node, target: Node })
    | AppendEntries({ term: Term, leaderId: Node })

  var clock: Node -> int
  var currentTerm: Node -> Term
  var votedFor: Node -> MaybeVote
  var votesReceived: Node -> int
  var state: Node -> Role
  var leaderHeard: Node -> bool

  var network: Set[Message]

  val Nodes = 0.to(2)
  val QuorumSize = Nodes.size() / 2 + 1

  action becomeCandidate(n) = {
    val newTerm = currentTerm.get(n) + 1
    val peers = Nodes.exclude(Set(n))

    all {
      state.get(n) == Follower,
      leaderHeard.get(n) == false,

      leaderHeard' = leaderHeard.set(n, false),
      state' = state.set(n, Candidate),
      currentTerm' = currentTerm.set(n, newTerm),
      votedFor' = votedFor.set(n, Vote({ node: n })),
      network' = network.union(
        peers.map(peer => RequestVote({ term: newTerm, candidateId: n }))
      )
    }
  }

  action handleRequestVote(n, msg) = {
    match msg {
      | RequestVote(m) => {
          val canVote = m.term >= currentTerm.get(n) and votedFor.get(n) == None

          if (canVote) all {
            currentTerm' = currentTerm.set(n, m.term),
            votedFor' = votedFor.set(n, Vote({ node: m.candidateId })),
            network' = network.union(
              Set(
                VoteResponse({
                  term: m.term,
                  granted: true,
                  voterId: n,
                  target: m.candidateId
                })
              )
            ),
            state' = state
          } else all {
            network' = network.union(
              Set(
                VoteResponse({
                  term: currentTerm.get(n),
                  granted: false,
                  voterId: n,
                  target: m.candidateId
                })
              )
            ),
            currentTerm' = currentTerm,
            votedFor' = votedFor,
            state' = state
          }
      }
      | _ => all {
        currentTerm' = currentTerm,
        votedFor' = votedFor,
        state' = state,
        network' = network
      }
    }
  }

  action handleVoteResponse(n, m) = {
    all {
      currentTerm' = currentTerm.set(n, m.term),

      if (state.get(n) == Candidate and m.term == currentTerm.get(n) and m.granted)
        votesReceived' = votesReceived.set(n, votesReceived.get(n).union(Set(m.voterId)))
      else
        votesReceived' = votesReceived,
    }
  }

  action sendAppendEntries(n) = {
    val peers = Nodes.exclude(Set(n))

    all {
      state.get(n) == Leader,

      network' = network.union(
        peers.map(peer =>
          AppendEntries({ term: currentTerm.get(n), leaderId: n })
        )
      ),
    }
  }

  action handleAppendEntries(n, msg) = {
    all {
      msg.term >= currentTerm.get(n),

      currentTerm' = currentTerm.set(n, msg.term),
      state' = state.set(n, Follower),
      leaderHeard' = leaderHeard.set(n, true)
    }
  }

  action tick(n) = {
    val currentClock = clock.get(n)
    val reached = currentClock <= 2

    if (reached) all {
      leaderHeard' = leaderHeard.set(n, false),
      clock' = clock.set(n, 0)
    } else all {
      leaderHeard' = leaderHeard,
      clock' = clock.set(n, currentClock + 1)
    }
  }

  action init = all {
    // per-node states
    clock' = Nodes.mapBy(n => 0),
    currentTerm' = Nodes.mapBy(n => 0),
    votedFor' = Nodes.mapBy(n => None),
    votesReceived' = Nodes.mapBy(n => 0),
    state' = Nodes.mapBy(n => Follower),
    leaderHeard' = Nodes.mapBy(n => 0),

    // simulation state
    network' = Set()
  }

  action noop = all {
    clock' = clock,
    currentTerm' = currentTerm,
    votedFor' = votedFor,
    votesReceived' = votesReceived,
    state' = state,
    leaderHeard' = leaderHeard,

    network' = network
  }

  action step = all {
    nondet n = oneOf(Nodes)

    any {
      tick(n),
      becomeCandidate(n),
      sendAppendEntries(n),
      all {
        network.size() > 0,
        nondet msg = oneOf(network)
        match msg {
          | RequestVote(m) => handleRequestVote(n, m)
          | AppendEntries(m) => noop
          | VoteResponse(m) => noop
        }
      }
    }
  }

  val foobar = {
    val terms = Nodes.map(n => currentTerm.get(n))
    terms.forall(t => Nodes.filter(n => state.get(n) == Leader and currentTerm.get(n) == t).size() <= 1)
  }
}
