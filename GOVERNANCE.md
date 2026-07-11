# Governance

This document describes the governance model for the Substrait Compliance Framework project.

## Table of Contents

- [Overview](#overview)
- [Roles and Responsibilities](#roles-and-responsibilities)
- [Decision Making](#decision-making)
- [Contribution Process](#contribution-process)
- [Release Process](#release-process)
- [Conflict Resolution](#conflict-resolution)
- [Changes to Governance](#changes-to-governance)

## Overview

The Substrait Compliance Framework is an open source project under the Substrait umbrella. The project follows a meritocratic governance model where contributors gain influence through their contributions and commitment to the project.

### Project Goals

- Provide comprehensive compliance testing for Substrait implementations
- Maintain high-quality, well-documented test suites
- Support multiple programming languages and platforms
- Foster an inclusive and welcoming community
- Ensure long-term sustainability and growth

### Principles

1. **Openness**: All project discussions, decisions, and development happen in public
2. **Meritocracy**: Influence is earned through contributions and commitment
3. **Transparency**: Decision-making processes are clear and documented
4. **Inclusivity**: We welcome contributors from all backgrounds
5. **Quality**: We maintain high standards for code, tests, and documentation
6. **Sustainability**: We plan for long-term project health

## Roles and Responsibilities

### Contributors

Anyone who contributes to the project in any way:
- Code contributions (features, bug fixes, tests)
- Documentation improvements
- Bug reports and feature requests
- Community support and discussions
- Code reviews and feedback

**Rights:**
- Submit pull requests
- Open issues
- Participate in discussions
- Vote in community polls

**Responsibilities:**
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md)
- Follow the [Contributing Guidelines](CONTRIBUTING.md)
- Be respectful and constructive

### Committers

Contributors who have demonstrated sustained commitment and quality contributions.

**Requirements:**
- 3+ months of active contributions
- 10+ merged pull requests OR significant documentation/community contributions
- Demonstrated understanding of project goals and standards
- Positive community interactions
- Nomination by a Maintainer
- Approval by majority of Maintainers

**Rights:**
- All Contributor rights
- Write access to the repository
- Ability to merge pull requests (following review requirements)
- Participate in Committer meetings
- Vote on Committer-level decisions

**Responsibilities:**
- Review pull requests promptly
- Help triage issues
- Mentor new contributors
- Maintain code quality standards
- Follow release procedures
- Participate in project discussions

### Maintainers

Committers who have demonstrated leadership and long-term commitment to the project.

**Requirements:**
- 6+ months as a Committer
- Significant contributions across multiple areas
- Demonstrated technical leadership
- Active participation in project governance
- Commitment to project sustainability
- Nomination by an existing Maintainer
- Approval by 2/3 majority of current Maintainers

**Rights:**
- All Committer rights
- Final decision authority on technical matters
- Ability to nominate Committers and Maintainers
- Participate in Maintainer meetings
- Vote on all project decisions
- Access to project infrastructure (CI/CD, domains, etc.)

**Responsibilities:**
- Set technical direction
- Manage releases
- Ensure project health and sustainability
- Resolve conflicts
- Represent the project in the Substrait community
- Mentor Committers
- Review and approve governance changes
- Respond to security issues

### Project Lead

A Maintainer who coordinates overall project direction and represents the project externally.

**Current Launch-State Assignment:**
- Until additional active Maintainers are formally added in [`MAINTAINERS.md`](MAINTAINERS.md), the Project Lead role is held by the sole named active Maintainer of record.
- At the current pre-release stage, the Project Lead is the release authority owner, security triage coordinator, and final escalation point for repository operations.

**Selection:**
- Once there are at least two active Maintainers, the Project Lead is elected by Maintainers annually
- Requires 2/3 majority vote
- Can be re-elected

**Additional Responsibilities:**
- Coordinate Maintainer meetings
- Represent project in Substrait governance
- Break tie votes when necessary
- Coordinate releases and announcements
- Manage project infrastructure
- Approve or delegate release execution according to the release authority rules below

## Decision Making

### Consensus-Based Decision Making

The project uses a consensus-based approach for most decisions:

1. **Proposal**: Anyone can propose changes via GitHub issues or discussions
2. **Discussion**: Community discusses the proposal openly
3. **Refinement**: Proposal is refined based on feedback
4. **Consensus**: Maintainers seek consensus among stakeholders
5. **Decision**: If consensus is reached, the proposal is accepted

### Voting

When consensus cannot be reached, decisions are made by voting:

#### Voting Eligibility

- **Technical Decisions**: Maintainers vote
- **Committer Nominations**: Maintainers vote
- **Maintainer Nominations**: Maintainers vote
- **Governance Changes**: Maintainers vote
- **Community Polls**: All Contributors can vote

#### Voting Thresholds

- **Simple Majority** (>50%): Technical decisions, Committer nominations
- **Supermajority** (≥2/3): Maintainer nominations, governance changes, removing roles
- **Unanimous**: Changes to Code of Conduct

#### Voting Process

1. Proposal is posted with clear description and rationale
2. Minimum 7-day discussion period
3. Vote is called with clear deadline (minimum 5 days)
4. Votes are cast publicly in the discussion thread
5. Results are announced and documented

### Types of Decisions

#### Technical Decisions

Examples: Architecture changes, API design, technology choices

- **Minor Changes**: Single Maintainer approval required
- **Moderate Changes**: Two Maintainer approvals required
- **Major Changes**: Consensus or majority vote of Maintainers

#### Process Decisions

Examples: Release schedule, CI/CD changes, documentation structure

- **Minor Changes**: Committer consensus
- **Major Changes**: Maintainer consensus or vote

#### Community Decisions

Examples: Events, communications, partnerships

- **Discussed in community forums**
- **Final decision by Maintainers**

## Contribution Process

### Standard Workflow

1. **Discuss**: For significant changes, open an issue first
2. **Develop**: Create a feature branch and implement changes
3. **Test**: Ensure all tests pass and add new tests
4. **Document**: Update documentation as needed
5. **Submit**: Open a pull request with clear description
6. **Review**: Address feedback from reviewers
7. **Merge**: Once approved, a Committer merges the PR

### Review Requirements

- **Minor Changes** (docs, small fixes): 1 approval from Committer or Maintainer
- **Moderate Changes** (features, refactoring): 2 approvals, at least 1 from Maintainer
- **Major Changes** (breaking changes, architecture): 2 Maintainer approvals

### Review Timeline

- Reviewers should respond within 3 business days
- PRs should be merged or closed within 14 days of approval
- Stale PRs (30+ days inactive) may be closed

## Release Process

### Release Types

- **Major Release** (X.0.0): Breaking changes, annual cadence
- **Minor Release** (0.X.0): New features, every 6-8 weeks
- **Patch Release** (0.0.X): Bug fixes, as needed

### Release Procedure

1. **Planning**: Maintainers agree on release scope and timeline
2. **Code Freeze**: Announced 1 week before release
3. **Testing**: Full test suite, integration tests, manual testing
4. **Release Candidate**: Tagged and announced for community testing
5. **Final Release**: After 3-7 day RC period with no critical issues
6. **Announcement**: Posted to all community channels

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for detailed steps.

### Release Authority

- **Current pre-release authority model**: Because the project currently has one named active Maintainer, release execution authority is held by the Project Lead / Release Maintainer of record listed in [`MAINTAINERS.md`](MAINTAINERS.md)
- **Patch Releases**: The Release Maintainer may execute after required validation and checklist completion
- **Minor Releases**: Require explicit approval by the Release Maintainer and documented release checklist completion; once multiple Maintainers exist, require 2 Maintainer approvals
- **Major Releases**: Require documented Maintainer consensus; until multiple Maintainers exist, no major stable release should be represented as community-approved governance consensus
- **Delegation**: Operational release steps may be delegated, but accountability for release approval and rollback decisions remains with the Release Maintainer of record

## Conflict Resolution

### Process

1. **Direct Discussion**: Parties attempt to resolve directly
2. **Mediation**: If unresolved, a neutral Maintainer mediates
3. **Maintainer Review**: If still unresolved, brought to Maintainer meeting
4. **Decision**: Maintainers make final decision by vote if necessary

### Code of Conduct Violations

Handled according to [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md):

1. Report to conduct@substrait.io
2. Maintainers review privately
3. Decision made within 7 days
4. Actions may include warning, temporary ban, or permanent ban

## Changes to Governance

### Amendment Process

1. **Proposal**: Submit PR with proposed changes to GOVERNANCE.md
2. **Discussion**: Minimum 14-day discussion period
3. **Vote**: Requires 2/3 supermajority of Maintainers
4. **Announcement**: Changes announced to community

### Review Schedule

- Governance document reviewed annually
- Updates proposed as needed based on project growth

## Emeritus Status

### Committers and Maintainers

- May step down voluntarily at any time
- Automatically moved to emeritus after 6 months of inactivity
- Can return to active status by resuming contributions
- Emeritus members retain recognition but not voting rights

### Recognition

- Listed in [MAINTAINERS.md](MAINTAINERS.md)
- Acknowledged in release notes
- Invited to project events and discussions

## Communication Channels

### Public Channels

- **GitHub Issues**: Bug reports, feature requests
- **GitHub Discussions**: General questions, proposals, announcements
- **Mailing List**: substrait-dev@googlegroups.com
- **Slack**: #substrait-compliance channel

### Private Channels

- **Maintainer Meetings**: Monthly video calls (notes published)
- **Security Issues**: security@substrait.io
- **Code of Conduct**: conduct@substrait.io

## Meetings

### Maintainer Meetings

- **Frequency**: Monthly, first Tuesday at 10:00 AM Pacific
- **Format**: Video call, recorded and published
- **Agenda**: Posted 48 hours in advance in GitHub Discussions
- **Notes**: Published within 48 hours after meeting
- **Attendance**: Maintainers required, Committers invited, open to observers

### Community Calls

- **Frequency**: Quarterly
- **Format**: Open video call
- **Purpose**: Project updates, demos, Q&A
- **Announcement**: 2 weeks in advance

## Project Infrastructure

### Repository Access

- **Public Repository**: github.com/substrait-io/substrait-compliance
- **Branch Protection**: main branch requires reviews and passing CI
- **Committer Access**: Write access to repository
- **Maintainer Access**: Admin access to repository and infrastructure

### Infrastructure Management

Maintainers collectively manage, or in the current launch state the Project Lead manages directly:
- GitHub repository settings
- CI/CD systems
- Domain names and websites
- Package registries (Maven, PyPI, crates.io, npm, etc.)
- Container registries
- Cloud resources (if any)

## Acknowledgments

This governance model is inspired by:
- [Apache Software Foundation](https://www.apache.org/foundation/governance/)
- [Kubernetes Governance](https://github.com/kubernetes/community/blob/master/governance.md)
- [Rust Governance](https://www.rust-lang.org/governance)

## Contact

For questions about governance:
- Open a discussion in [GitHub Discussions](https://github.com/substrait-io/substrait-compliance/discussions)
- Email maintainers: See [MAINTAINERS.md](MAINTAINERS.md)
- Join community calls

---

**Version**: 1.0
**Last Updated**: June 26, 2026
**Next Review**: June 26, 2027