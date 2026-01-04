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
    | RequestVote({ to: Node, from: Node, term: Term })
    | VoteResponse({ to: Node, from: Node, term: Term, granted: bool })
    | AppendEntries({ to: Node, from: Node, term: Term })

  var currentTerm: Node -> Term
  var leaderHeard: Node -> bool
  var votedFor: Node -> MaybeVote
  var votesReceived: Node -> Set[Node]
  var role: Node -> Role

  var network: Set[Message]

  val Nodes = 0.to(2)
  val QuorumSize = Nodes.size() / 2 + 1

  action becomeCandidate(n) = {
    val newTerm = currentTerm.get(n) + 1
    val peers = Nodes.exclude(Set(n))

    all {
      role.get(n) != Leader,
      leaderHeard.get(n) == false,

      votesReceived' = votesReceived.set(n, Set(n)),
      leaderHeard' = leaderHeard,
      role' = role.set(n, Candidate),
      currentTerm' = currentTerm.set(n, newTerm),
      votedFor' = votedFor.set(n, Vote({ node: n })),
      network' = network.union(
        peers.map(peer => RequestVote({ to: peer, from: n, term: newTerm }))
      )
    }
  }

  action handleRequestVote(payload, msg) = all {
    val canVote = or {
      payload.term == currentTerm.get(payload.to) and votedFor.get(payload.to) == Vote({ node: payload.from }),
      payload.term == currentTerm.get(payload.to) and votedFor.get(payload.to) == None,
      payload.term > currentTerm.get(payload.to)
    }

    if (canVote) all {
      currentTerm' = currentTerm.set(payload.to, payload.term),
      votesReceived' = votesReceived.set(payload.to, Set()),
      votedFor' = votedFor.set(payload.to, Vote({ node: payload.from })),
      role' = role.set(payload.to, Follower),
      leaderHeard' = leaderHeard.set(payload.to, true),

      network' = network.exclude(Set(msg)).union(
        Set(VoteResponse({ to: payload.from, from: payload.to, term: payload.term, granted: true }))
      )
    } else all {
      currentTerm' = currentTerm,
      votesReceived' = votesReceived,
      votedFor' = votedFor,
      role' = role,
      leaderHeard' = leaderHeard,
      
      network' = network.exclude(Set(msg)).union(
        Set(VoteResponse({ to: payload.from, from: payload.to, term: currentTerm.get(payload.to), granted: false }))
      )
    }
  }

  action handleVoteResponse(payload, msg) = {
    val peers = Nodes.exclude(Set(payload.to))
    val updatedVotes = votesReceived.get(payload.to).union(Set(payload.from))

    all {
      payload.term == currentTerm.get(payload.to),
      role.get(payload.to) == Candidate,

      votedFor' = votedFor,
      currentTerm' = currentTerm,
      leaderHeard' = leaderHeard,

      if (payload.granted) all {
        if (updatedVotes.size() >= QuorumSize) {
          role' = role.set(payload.to, Leader)
        } else {
          role' = role
        },
        votesReceived' = votesReceived.set(payload.to, updatedVotes),
        network' = network.exclude(Set(msg)).union(
          peers.map(peer => AppendEntries({ to: peer, from: payload.to, term: currentTerm.get(payload.to) }))
        )
      } else all {
        role' = role,
        votesReceived' = votesReceived,
        network' = network.exclude(Set(msg))
      }
    }
  }

  action sendAppendEntries(n) = {
    val peers = Nodes.exclude(Set(n))

    all {
      role.get(n) == Leader,

      votedFor' = votedFor,
      leaderHeard' = leaderHeard,
      role' = role,
      votesReceived' = votesReceived,
      currentTerm' = currentTerm,

      network' = network.union(
        peers.map(peer => AppendEntries({ to: peer, from: n, term: currentTerm.get(n) }))
      )
    }
  }

  action handleAppendEntries(payload, msg) = all {
    payload.term >= currentTerm.get(payload.to),

    votedFor' = votedFor,
    votesReceived' = votesReceived,

    leaderHeard' = leaderHeard.set(payload.to, true),
    currentTerm' = currentTerm.set(payload.to, payload.term),
    role' = role.set(payload.to, Follower),
    network' = network.exclude(Set(msg))
  }

  action expireLeaderHeard(n) = all {
    currentTerm' = currentTerm,
    leaderHeard' = leaderHeard.set(n, false),
    votedFor' = votedFor,
    votesReceived' = votesReceived,
    role' = role,
    network' = network 
  } 

  action noop = all { 
    currentTerm' = currentTerm,
    leaderHeard' = leaderHeard,
    votedFor' = votedFor,
    votesReceived' = votesReceived,
    role' = role,
    network' = network 
  } 

  action init = all {
    // per-node states
    currentTerm' = Nodes.mapBy(n => 0),
    leaderHeard' = Nodes.mapBy(n => false),
    votedFor' = Nodes.mapBy(n => None),
    votesReceived' = Nodes.mapBy(n => Set()),
    role' = Nodes.mapBy(n => Follower),

    // simulation state
    network' = Set()
  }

  action consumeNetwork = all {
    network.size() > 0,

    nondet msg = network.oneOf()
    match msg {
      | RequestVote(payload) => handleRequestVote(payload, msg)
      | VoteResponse(payload) => handleVoteResponse(payload, msg)
      | AppendEntries(payload) => handleAppendEntries(payload, msg)
    }
  }

  action step = all {
    nondet n = Nodes.oneOf()
    any {
      expireLeaderHeard(n),
      becomeCandidate(n),
      sendAppendEntries(n),
      consumeNetwork,
    }
  }

  val foobar = {
    val terms = Nodes.map(n => currentTerm.get(n))
    terms.forall(term => and {
      // Nodes need to have received at least QuorumSize
      // amount of votes to be the leader of a term
      Nodes.filter(node => and {
        role.get(node) == Leader,
        currentTerm.get(node) == term
      }).forall(node => votesReceived.get(node).size() >= QuorumSize),

      // Candidates need to vote on themselves when
      // they change their role to candidate
      Nodes.filter(node => and {
        role.get(node) == Candidate,
        currentTerm.get(node) == term
      }).forall(node => and {
        votesReceived.get(node).size() >= 1,
        votesReceived.get(node).contains(node)
      }),

      // Maximum number of votes received must be the number of nodes
      Nodes.forall(node => votesReceived.get(node).size() <= Nodes.size()),

      // Each term must have at most one leader
      Nodes.filter(node => and {
        role.get(node) == Leader,
        currentTerm.get(node) == term
      }).size() <= 1,
    })
  }
}
