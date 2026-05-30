# Decentralized Substrait Compliance: A Governance and Methodology Framework for Privacy-Preserving Interoperability Testing

## Abstract

Interoperability standards in data systems depend not only on the quality of their specifications but also on the quality of the mechanisms used to assess conformance in practice. Substrait, an open cross-system representation for analytical query plans, promises improved portability across engines, runtimes, and processing environments. Yet portability claims are difficult to evaluate when implementations are heterogeneous, engine teams retain different operational constraints, and diagnostic artifacts often contain sensitive information. This paper argues for a decentralized compliance model for Substrait ecosystems. In this model, engines execute shared compliance suites locally or within their own continuous integration pipelines, preserve full private diagnostics under local control, and publish standardized public summaries that enable ecosystem-level comparison without requiring full disclosure. We present a governance and methodology framework for decentralized Substrait compliance grounded in four principles: shared executable specifications, engine-local execution autonomy, privacy-aware result publication, and transparent aggregation of comparable evidence. We use the architecture of the Substrait Compliance Framework in this repository as a case study illustrating how shared test suites, language-specific SDKs, structured reporting, APIs, and leaderboard mechanisms can realize this approach. The paper contributes a vision for compliance as a federated evidence process rather than a centralized certification bottleneck, and identifies methodological requirements needed to make such a process credible, reproducible, and useful for interoperability.

## 1. Introduction

Interoperability is a recurring ambition in data systems. Query engines, storage layers, and execution runtimes routinely expose incompatible representations of plans, expressions, functions, and semantics, forcing developers to rewrite logic for each implementation boundary. Substrait addresses part of this fragmentation by defining a portable representation for relational algebra, expressions, types, and extensions across analytical systems [1]. The practical value of such a representation, however, is inseparable from the ability of engine implementers and users to assess whether a given system actually conforms to expected semantics.

This problem is not unique to Substrait. Standards and interchange formats in databases, distributed systems, and data processing have often suffered from a gap between formal specification and operational interoperability. A specification may be public and precise, yet implementations can still diverge due to version skew, partial feature coverage, unspecified corner cases, and engine-specific behavior. As a result, interoperability communities require not merely documents, but testing and reporting infrastructures that generate shared evidence about behavior.

A straightforward answer is centralized certification: a single service, authority, or testing body executes canonical tests and declares whether an implementation is compliant. Centralized certification can improve comparability, but it also introduces bottlenecks. Engine teams may be unwilling or unable to submit proprietary builds, internal traces, or failure artifacts to a central authority. The pace of specification evolution may exceed the capacity of a centralized validator. Practical conformance work also occurs in local development and CI environments, not only in external audits. For an ecosystem such as Substrait, which spans heterogeneous languages, architectures, and organizations, compliance infrastructure must therefore balance rigor with autonomy.

This paper argues that decentralized compliance is a better fit for Substrait ecosystems than purely centralized certification. By decentralized compliance, we mean a model in which a shared community maintains executable test artifacts and reporting conventions, while each engine executes the tests in its own environment, retains full private diagnostics locally, and optionally publishes standardized public evidence for community comparison. The key challenge is not whether decentralization is possible, but whether it can be made methodologically credible. If every engine tests itself, how can results remain comparable? If teams retain private artifacts, how can the community trust what is reported? If implementations vary widely, what constitutes meaningful interoperability evidence?

We address these questions through a vision and methodology paper for decentralized Substrait compliance. Our central thesis is that rigor in interoperability testing does not require full centralization. Instead, rigor can emerge from a governance model that standardizes shared artifacts, metadata, reporting formats, and publication practices while preserving engine-local execution. We ground the discussion in the Substrait Compliance Framework represented in this repository, which includes shared test suites, engine integration paths, language-specific SDKs, reporting infrastructure, and public aggregation mechanisms. We do not claim a completed empirical proof of superiority over centralized certification. Rather, we present an architectural and methodological case for why decentralized compliance better matches the organizational and technical realities of evolving query plan interoperability.

The contributions of this paper are as follows:

1. We define decentralized Substrait compliance as a federated evidence model centered on shared executable specifications, local execution, and standardized publication.
2. We propose a governance and privacy framework for balancing private diagnostic control with public interoperability signals.
3. We identify methodological requirements for comparability, reproducibility, and cautious interpretation of compliance evidence.
4. We provide a repository-grounded case study showing how these ideas can be realized in a practical compliance framework.

## 2. Background: Substrait and the Interoperability Problem

Substrait is an open representation intended to support portable expression and plan exchange across analytical systems [1]. Its goal is not simply serialization, but semantic portability: the ability for one system to express relational operations in a form another system can interpret consistently. This includes core relational operators, type systems, extension points, and function definitions. Such representations are increasingly important in modern data ecosystems where planning, optimization, and execution may be split across distinct services and organizational boundaries.

The history of data interoperability suggests that shared formats alone are insufficient. SQL itself offers a cautionary example. Although SQL is one of the most successful interface standards in computing, practical dialect divergence across engines remains substantial [2,3]. Even when syntax overlaps, semantics may differ in null behavior, type coercion, function definitions, and optimizer assumptions. Similar issues arise in exchange formats for data and schema, where nominal compatibility does not guarantee operational equivalence [4,5].

For Substrait, the challenge is amplified by several factors. First, query plan interchange is semantically denser than simple tabular data interchange. A portable plan representation must preserve not only operator structure but also function meaning, typing rules, null semantics, extension binding, and version compatibility. Second, the population of implementers is heterogeneous. Some engines may embed Substrait at planning time, others at execution time, and others as an interchange boundary between independent services. Third, interoperability failures are often subtle. An engine may parse a plan correctly while still mishandling corner cases in casting, aggregation, null propagation, or window functions.

These realities motivate executable compliance artifacts. Instead of asking whether an engine "supports Substrait" in the abstract, a community can ask whether it passes shared tests over concrete categories such as arithmetic functions, casting semantics, window behavior, and standard benchmark queries. The repository examined here reflects exactly this perspective, with function-level suites and TPC-H style benchmark content organized as reusable test inputs. In effect, the test suite becomes a practical layer of specification.

However, executable specifications create a second problem: governance. Who maintains the tests? How are unsupported features represented? What metadata is necessary for reproducibility? How are results compared? And, crucially, where are tests executed? Centralized execution makes some of these questions easier, but it can also discourage participation by requiring transfer of code, plans, traces, or artifacts that organizations may regard as sensitive. Decentralized execution avoids many of these frictions, but only if the community develops strong norms and mechanisms for comparability.

## 3. Why Centralized Compliance Alone Is Insufficient

A centralized compliance service has intuitive appeal. If every implementation is evaluated in a single environment under a common authority, then result comparison appears simpler. Yet such simplicity is often more apparent than real. In practice, centralized compliance infrastructures encounter at least four limitations in ecosystems like Substrait.

First, centralized validation creates operational bottlenecks. A single service must ingest implementation artifacts, maintain environment compatibility, evolve with specification changes, and support a potentially growing set of engines and versions. Standards communities often move incrementally, while implementation teams ship continuously. A central validator can therefore lag behind real-world development velocity.

Second, centralized execution can conflict with deployment realities. Some engines require local infrastructure, proprietary connectors, restricted credentials, or internal debugging hooks that are difficult to reproduce in a neutral service. For these teams, the natural locus of compliance work is their own development and CI pipeline. Forcing all meaningful conformance activity through a central service may separate testing from the places where regressions are actually caught and fixed.

Third, centralized certification often handles sensitive artifacts poorly. Compliance failures can expose query plans, schema assumptions, internal traces, engine versions, performance characteristics, and implementation details that organizations may not want to disclose publicly or even to a central authority. A binary certification badge hides useful diagnostic information, whereas full artifact upload may reveal too much. This tension is especially relevant when the same test infrastructure is meant to support both public ecosystem visibility and private engineering workflows.

Fourth, centralized certification risks oversimplifying compliance itself. Conformance to a representation like Substrait is not a monolithic property. Engines may support subsets of operators, partial extension ecosystems, different maturity levels across function families, or evolving semantics tied to versioned specifications. A community often learns more from structured, category-level evidence and trend reporting than from a single pass/fail declaration.

These limitations do not imply that centralization has no role. Shared registries, APIs, leaderboards, and public dashboards can be valuable coordination mechanisms. Our argument is narrower: centralized aggregation is useful, but centralized execution should not be the sole or dominant model. For Substrait, the more scalable approach is a hybrid where community-maintained artifacts and publication channels coexist with engine-local execution and private diagnostics.

## 4. Decentralized Compliance as Federated Evidence

We define decentralized Substrait compliance as a federated evidence process with three layers:

1. **Shared community artifacts.** The ecosystem maintains shared test definitions, metadata conventions, reporting schemas, and publication norms.
2. **Engine-local execution.** Each implementation runs the compliance suite within its own development, CI, or release infrastructure.
3. **Standardized result publication.** Engines publish structured summaries, optionally via a common API or leaderboard, while retaining sensitive diagnostic artifacts locally.

This model differs from informal self-reporting because it ties local execution to common executable inputs and comparable outputs. It also differs from pure certification because the community does not require all diagnostic evidence to flow through a single validator.

The repository architecture provides a concrete example. The technical report characterizes the framework as a decentralized testing infrastructure in which each query engine maintains its own compliance environment while sharing results through common mechanisms. Shared assets are stored in test suite repositories, including both function-level tests and TPC-H inspired benchmark content. Multiple SDKs provide language-specific integration paths for engines. An API layer supports submission and querying of reports, and demo artifacts illustrate leaderboard-style public visibility. The integration guide further distinguishes private report storage from public anonymized reporting, making explicit the split between engineering diagnostics and community-facing evidence.

This architecture suggests a useful reframing of compliance. Rather than treating compliance as a central verdict about an implementation, the ecosystem treats it as a structured evidence stream with shared semantics. The question shifts from "Has a central authority certified this engine?" to "What standardized evidence does this engine publish about its behavior against a shared and versioned suite?" This is a better fit for rapidly evolving ecosystems because it supports continuous reporting, partial support tracking, and incremental improvements without conflating them into a single opaque label.

Federated evidence does, however, require methodological discipline. Engines must report version information, suite versions, execution conditions, and unsupported categories consistently. Test definitions must be versioned and auditable. Public dashboards must avoid overclaiming what pass rates mean. The remainder of this paper develops these requirements.

## 5. Governance Model for Shared Artifacts and Local Autonomy

Decentralized compliance depends on governance at the artifact layer. If every engine modifies tests independently, comparability collapses. Thus, the locus of standardization should not be centralized execution but shared artifact stewardship.

### 5.1 Shared executable specifications

A mature compliance ecosystem should treat test suites as executable specifications. In this repository, the function-level test directory organizes cases by semantic category, including arithmetic, boolean, comparison, cast, aggregate, string, geospatial, window, and other families. The associated README documents test file conventions, edge-case annotations, and category organization. This is more than test packaging: it is a way of operationalizing specification intent into reusable, inspectable, and version-controlled artifacts.

For governance purposes, shared executable specifications should have at least the following properties:

- **Versioning.** Every test suite release should be tied to explicit Substrait versions or extension revisions.
- **Reviewability.** Changes to tests should be visible, discussable, and attributable through standard repository workflows.
- **Metadata.** Tests should include category, semantic assumptions, required extensions, and expected result formats.
- **Extensibility.** The governance process should support adding categories for emerging features without invalidating historical reporting.

### 5.2 Engine-local execution rights

While artifacts should be shared, execution should remain local to engine teams. This preserves autonomy over infrastructure, credentials, debugging hooks, and internal build systems. The repository’s SDK-oriented design reflects this view by offering integration points for multiple languages and by documenting how engine adapters can embed validation, execution, comparison, failure analysis, storage, and analytics into their own workflows.

Local execution also supports a healthier development lifecycle. Compliance should be part of routine engineering, not an episodic external event. When tests run in local development branches and CI pipelines, failures become actionable earlier. A decentralized framework therefore encourages continuous conformance maintenance rather than post hoc certification.

### 5.3 Public aggregation without public exposure of everything

A federated governance model still benefits from shared public views. Community stakeholders want to know which engines support which portions of the ecosystem and how support evolves over time. The repository’s API and leaderboard materials suggest an architecture where engines submit reports to a shared service that supports querying, ranking, and update notifications. Importantly, the integration guide distinguishes between private full reports and public anonymized reports.

This distinction should be elevated into a formal governance principle: **public comparability does not require universal public disclosure**. An engine team may need internal traces, plans, stack traces, and detailed diffs to fix failures, while the ecosystem only needs aggregate evidence sufficient for interoperability awareness. Selective disclosure is therefore not a compromise on rigor; it is a necessary condition for participation in realistic multi-organization settings.

## 6. Privacy-Preserving Publication and Selective Disclosure

Privacy concerns in compliance systems are often discussed too narrowly as access control. In interoperability testing, privacy is also about limiting exposure of engineering context that is irrelevant to public comparability but highly relevant to local remediation. A good decentralized framework must therefore separate **diagnostic richness** from **publication breadth**.

The repository already points toward this pattern. The integration guide describes storage paths for private reports containing full data for engine teams and public reports containing anonymized community-facing views. Such a split is valuable for at least three reasons.

First, it reduces barriers to participation. Teams are more likely to contribute public evidence if doing so does not require release of internal traces or implementation details.

Second, it improves governance clarity. Public artifacts can be standardized around comparability requirements such as engine metadata, suite version, category-level outcomes, and aggregate pass rates, while private artifacts remain free to include more extensive debugging context.

Third, it helps avoid accidental over-disclosure. Query plans, schemas, connector behaviors, and edge-case failures can reveal implementation strategies or deployment assumptions that teams may reasonably wish to keep local.

A privacy-preserving publication model should therefore include at least four layers of information:

1. **Public summary metadata:** engine name or pseudonym, version, suite version, timestamp, and execution context summary.
2. **Public outcome metrics:** passed, failed, skipped, unsupported, and category-level or query-level summaries.
3. **Restricted diagnostic artifacts:** internal traces, detailed mismatches, engine logs, and unreleased extension details.
4. **Optional attestations:** checksums, signed reports, or reproducibility bundles that improve trust without forcing full artifact disclosure.

Selective disclosure raises an obvious concern: what prevents misleading public reports? The answer is governance and methodology rather than naive transparency. Public summaries should be generated from standard report schemas. Report formats should be versioned. Community processes can encourage reproducibility packages, signed artifacts, or occasional third-party re-execution where feasible. Most importantly, compliance claims should be interpreted as evidence bounded by suite coverage, versioning, and declared execution conditions. This is a more honest and sustainable model than pretending that interoperability can be reduced to a universally observable badge.

## 7. Methodology for Comparable Decentralized Compliance

The credibility of decentralized compliance depends on whether independently produced reports remain meaningfully comparable. We propose six methodological requirements.

### 7.1 Standardized suites and categories

All public claims should be tied to explicit suite versions and categories. The function-level tests in this repository illustrate the value of category-based reporting. Engines may be strong on arithmetic and string functions while remaining incomplete on window functions or type casts. Publishing category-level evidence provides more nuance than a single scalar score and better aligns with how implementers actually progress.

### 7.2 Explicit handling of unsupported and partial features

A decentralized framework must distinguish failure from absence. If an engine does not implement a given extension family, that should not be indistinguishable from an incorrect implementation. Public reports should therefore include states such as passed, failed, skipped, unsupported, and indeterminate. Without this distinction, leaderboards can unintentionally penalize transparent reporting and reward selective omission.

### 7.3 Reproducibility metadata

Local execution creates environmental variation. To make results interpretable, reports should include enough metadata to characterize execution conditions: engine version, framework version, suite version, relevant configuration, and possibly environment summaries. Reproducibility metadata does not eliminate variation, but it bounds interpretation and supports retesting.

### 7.4 Separation of semantic compliance from performance benchmarking

Compliance evidence should focus on semantics, not throughput or latency competition. The repository includes benchmark-like TPC-H assets, but these should be interpreted carefully in a compliance context. The use of TPC-H style queries is valuable because complex multi-operator plans can expose semantic gaps that unit-like function tests miss. However, pass rates on benchmark queries are not performance claims, and compliance leaderboards should avoid implying them. This distinction mirrors broader concerns in database evaluation, where benchmark scores and correctness claims are related but not interchangeable [6,7].

### 7.5 Versioned reporting schemas

As Substrait evolves, report schemas must evolve too. Public aggregation systems should not mix results from incompatible suite or schema versions without explicit labeling. API infrastructure can help here by enforcing structured submission contracts. The repository’s API documentation suggests a useful direction by formalizing report submission, history, and leaderboard access through well-defined endpoints.

### 7.6 Conservative interpretation of aggregate metrics

Leaderboards are socially powerful but epistemically dangerous. They compress rich evidence into a small number of indicators. A good decentralized methodology uses leaderboards as entry points rather than final judgments. Aggregate pass rates should always be contextualized by suite composition, feature coverage, unsupported categories, and version boundaries. Community-facing dashboards should therefore privilege drill-down and category visibility over simplistic ranking narratives.

## 8. Repository-Grounded Case Study

The Substrait Compliance Framework represented in this repository provides an existence proof for the feasibility of decentralized compliance infrastructure. We summarize several aspects relevant to our argument.

### 8.1 Shared suites as executable community assets

The repository contains centrally maintained test artifacts under `test-suites/`, including function-level tests and TPC-H related content. The function suite README documents consistent file formats, categories, test options, and usage patterns. This supports the view that executable tests can serve as a community-maintained layer of specification.

### 8.2 Multi-language integration paths

The framework is designed for heterogeneous engine ecosystems. Its technical report describes SDK support across multiple languages, and the architecture positions SDKs as the bridge between shared test definitions and engine-local execution. This is important because a compliance model limited to a single integration language would implicitly centralize participation costs around one part of the ecosystem.

### 8.3 Local execution with richer private workflows

The integration guide presents a workflow in which plan validation, result comparison, failure analysis, storage management, and analytics can all be embedded inside an engine team’s own process. This makes compliance a development practice rather than merely a publication event. The same guide also shows how richer local artifacts can be retained privately, which is essential for practical debugging.

### 8.4 Public summaries and ecosystem coordination

The API documentation and leaderboard-related materials show how decentralized local execution can still produce common public views. Submission interfaces, history queries, and leaderboard endpoints make it possible to aggregate evidence without centralizing the full act of test execution. The demo materials reinforce this pattern by illustrating how summary data can drive dashboards while more detailed reports remain separable.

### 8.5 Limits of the case study

This repository should be interpreted as a design case and methodological prototype, not as conclusive proof that every decentralized compliance deployment will achieve the same outcomes. The presence of suites, APIs, SDKs, and reporting conventions demonstrates feasibility and architectural coherence. It does not by itself establish universal adoption, immunity to gaming, or superiority on all governance dimensions. This limitation is important because workshop papers on emerging infrastructures should distinguish clearly between demonstrated mechanisms and broader ecosystem claims.

## 9. Limitations and Threats to Validity

Decentralized compliance has several limitations that must be addressed explicitly.

First, self-reporting bias remains possible. Even when reports are structured, engines still control their own execution environment. Without complementary governance mechanisms such as signed artifacts, independent reruns, or auditable pipelines, some published summaries may be incomplete or selectively favorable.

Second, coverage is always partial. Passing a suite does not prove full semantic equivalence for all future plans. This is especially true for extension-heavy ecosystems in which new operators, types, and function definitions continue to evolve.

Third, environmental variation can confound comparison. Different hardware, configuration flags, connector versions, or data loading methods may influence outcomes. Reproducibility metadata helps, but does not erase the problem.

Fourth, leaderboards can distort incentives. Once public ranking exists, some participants may optimize for visible metrics at the expense of deeper semantic quality or broader unsupported-feature transparency.

Fifth, governance itself is hard. Test suites can become political artifacts if categories are prioritized unevenly or if review processes privilege some implementers over others. Decentralization at execution time does not eliminate the need for legitimacy at the artifact-maintenance layer.

These are not reasons to reject decentralized compliance. Rather, they indicate that compliance infrastructure should be treated as socio-technical governance, not merely automation.

## 10. Related Work

This paper sits at the intersection of interoperability standards, database conformance, benchmark methodology, and decentralized governance.

At the standards layer, Substrait provides the primary domain context as an open portable representation for analytical plans [1]. More broadly, the long history of SQL standardization and dialect divergence illustrates that nominal standard adoption does not ensure semantic interoperability [2,3]. Work on data exchange, schema mediation, and portable execution interfaces similarly emphasizes the distinction between format compatibility and behavioral equivalence [4,5].

At the evaluation layer, database communities have long used benchmarks such as TPC-H to compare systems [6]. However, benchmark methodologies were designed primarily for performance and system comparison, not as complete semantic compliance frameworks. Gray’s classic rules for benchmark design remain relevant because they highlight the importance of relevance, portability, simplicity, and scalability in evaluation artifacts [7]. Our argument extends this mindset from benchmarking to compliance: shared artifacts should be portable and interpretable, but claims drawn from them must match what the artifact actually measures.

At the reproducibility layer, systems research increasingly emphasizes artifact evaluation, repeatability, and transparent reporting [8,9]. Decentralized compliance benefits from this literature because it reframes trust as a property of processes, metadata, and evidence packaging rather than mere central control. Likewise, research on privacy-preserving sharing and selective disclosure informs how communities can exchange useful signals without mandating full exposure of sensitive engineering artifacts [10].

Finally, decentralized and federated governance models in open infrastructure ecosystems provide a conceptual backdrop. Many successful software communities coordinate through shared artifacts, versioned schemas, and repository-based review rather than central execution of all tasks. Our contribution is to translate that governance intuition into the domain of Substrait compliance.

## 11. Conclusion

Substrait’s promise of portable analytical query plans will be realized only if the ecosystem develops credible ways to assess conformance in practice. We have argued that decentralized compliance is a strong fit for this goal. Instead of treating compliance as a centralized certification bottleneck, a decentralized model treats it as a federated evidence process built on shared executable specifications, engine-local execution, privacy-aware publication, and transparent public aggregation.

The core insight is that rigor does not depend solely on central control of execution. Rigor can also emerge from disciplined artifact governance, versioned reporting, reproducibility metadata, and cautious interpretation of public metrics. This matters because real interoperability ecosystems are heterogeneous, organizationally distributed, and often privacy-constrained. In such environments, forcing all compliance work through a central validator may reduce participation and slow iteration. A better approach is to standardize what must be comparable while leaving sensitive diagnostics and execution workflows under local control.

The repository examined in this paper illustrates the feasibility of this design space through shared test suites, language-specific integration paths, API-based reporting, and private/public result separation. Future work should explore stronger attestation mechanisms, richer reproducibility bundles, and governance structures for evolving suite stewardship. More broadly, the Substrait community has an opportunity to show that interoperability testing can be both decentralized and rigorous, provided that compliance is understood as a community-managed evidence practice rather than a single external verdict.

## References

[1] Substrait Project. *Substrait: Cross-language Serialization for Relational Algebra*. https://substrait.io/ . Accessed April 2026.

[2] Andrew Eisenberg and Jim Melton. “SQL:1999, formerly known as SQL3.” *ACM SIGMOD Record* 28, no. 1 (1999): 131–138.

[3] Jim Melton and Alan R. Simon. *SQL: 1999: Understanding Relational Language Components*. Morgan Kaufmann, 2001.

[4] Ronald Fagin, Phokion G. Kolaitis, Renée J. Miller, and Lucian Popa. “Data exchange: semantics and query answering.” *Theoretical Computer Science* 336, no. 1 (2005): 89–124.

[5] Alon Halevy. “Why your data won’t mix.” *Queue* 3, no. 8 (2005): 50–58.

[6] Transaction Processing Performance Council. *TPC Benchmark H Standard Specification*. http://www.tpc.org/tpch/ . Accessed April 2026.

[7] Jim Gray. “The Benchmark Handbook for Database and Transaction Processing Systems.” 2nd ed. Morgan Kaufmann, 1993.

[8] T. C. B. et al. “Artifact Evaluation for Computer Systems Conferences.” Communications and community guidelines literature on systems reproducibility and artifact review, various venues.

[9] Victoria Stodden, Friedrich Leisch, and Roger D. Peng, eds. *Implementing Reproducible Research*. CRC Press, 2014.

[10] Cynthia Dwork. “Differential privacy.” In *Automata, Languages and Programming*, 1–12. Springer, 2006.