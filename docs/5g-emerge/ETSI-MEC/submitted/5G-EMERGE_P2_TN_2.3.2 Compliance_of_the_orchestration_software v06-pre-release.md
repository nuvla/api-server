# 5G-EMERGE_P2_TN_2.3.2 Compliance_of_the_orchestration_software v06-pre-release

- Source PDF: `5G-EMERGE_P2_TN_2.3.2 Compliance_of_the_orchestration_software v06-pre-release.pdf`
- Generated from embedded PDF text

<!-- Page 1 -->

## WP 2.3.2 – Compliance of the

orchestration software with ETSI-MEC, 3GPP standards ESA Contract No. 4000138171/22/NL/AF The European Space Agency funds the 5G Emerge project. The information and views set out in this deliverable are those of the author(s) and do not necessarily reflect the official opinion of the Consortium partners. The information in this document is provided “as is”, and no guarantee or warranty is given that the information fits any specific purpose. This document complements the Consortium Agreement and its Amendments. This document presents the situation at the date of the version number and will be periodically reviewed and updated.

<!-- Page 2 -->

Ref: 5GEMG-P2-TN2.3.2 Title: Technical Note on Compliance of the orchestration software with ETSI-MEC, 3GPP standards WP 2.3.2 – Compliance of the orchestration software with ETSI- MEC, 3GPP standards Project: 5G-EMERGE Phase 2 ESA Contract No. 4000138171/22/NL/AF Version: v06 Status: Submitted Date of issue: 12.02.2026 No. of pages: 69 Ref: 5GEMG-P2-TN2.3.2 Prepared by: Giacomo Bernini (Nextworks), Giada Landi (Nextworks), Matteo Ravalli (Nextworks), Agathe Veillon (SixSq), Konstantin Skaburskas (SixSq), Alessandro Bellucci (SixSq) Reviewed by: Giacomo Bernini – Nextworks Revised by: Giacomo Bernini – Nextworks Approved by: George Wright – Broadcast Critical Authorized by: George Wright – Broadcast Critical

<!-- Page 3 -->

## Distribution List

Company Arctic Space Technologies AB BFBS Cysec European Broadcasting Union Eutelsat Fondazione LINKS FTA Communication Technologies SARL G-Core HPE ITALIANA Humans not robots Keysight FI Keysight UK M.B.I. S.r.l. MAGISTER SOLUTIONS MediaTek FI MediaTek UK MinWave Nextworks Northbase Oulu University RAI - RADIOTELEVISIONE ITALIANA SPA (RAI) RomARS SES Techcom S.A. SixSq SA SnT (Uni Luxembourg) Swiss TXT Telenor (Research) ASA TNO Varnish Software AB Verkotan Viasat Antenna Systems SA VTT

<!-- Page 4 -->

## Change Record

Issue and Revision Sections affected by the Change reason number changes Issue: 01 Date: 12.05.2025 All First issue Pages: 18 Issue: 02 Added more content in ETSI Date: 19.05.2025 Section 2 MEC and 3GPP tech watch Pages: 25

## Executive Summary

Reference Documents Issue: 03 Added section 3, executive Acronyms Date: 26.05.2025 summary, acronyms, ref. Introduction Pages: 37 documents, conclusions Section 3 Conclusions Issue: 04 Updates to address MS1 Date: 25.06.2025 Section 5 RIDs: added introduction text Pages: 37 in section 5 and updated title Issue: 05 Updated the template to Date: 14.11.2025 Cover Page new TN template. Pages: 40 Updated 2.2.3 on 3GPP Edge Computing Management; Section 2.2.3 Added 2.4 on security Section 2.4 aspects; Added 2.5.1 for ETSI Section 2.5.1 Issue: 06 MEC and 3GPP alignment; Section 3 Date: 12.02.2026 Updated section 3 with Section 4 Pages: 69 assessment of 3GPP Section 5 compliance; Added section 4 Section 6 for feasibility study; Minor updates to sections 5 and 6; Added Annex A

<!-- Page 5 -->

## Table of Contents

- LIST OF FIGURES ...................................................................................................................................................................... .... 4
- LIST OF TABLES ........................................................................................................................................................................ .... 4
- LIST OF APPLICABLE AND REFERENCE DOCUMENTS......................................................................................................... .... 5
- LIST OF ACRONYMS ................................................................................................................................................................. .... 6
- 1 Introduction  .... 8
- 1.1 Objective of the Document  .... 8
- 1.2 Structure of the Document  .... 8
- 1.3 Relation to Other Work Packages in 5G-EMERGE  .... 8
- 2 Technology watch on relevant edge computing standards  .... 10
- 2.1 ETSI MEC .... 12
- 2.2 3GPP  .... 18
- 2.2.1 CAPIF  .... 21
- 2.2.2 SEAL  .... 23
- 2.2.3 EDGEAPP  .... 24
- 2.3 Industry Forums  .... 26
- 2.3.1 GSMA  .... 26
- 2.3.2 TM Forum  .... 28
- 2.4 Security Aspects  .... 29
- 2.4.1 ETSI MEC  .... 29
- 2.4.2 3GPP  .... 31
- 2.5 Standardization landscape harmonization  .... 32
- 2.5.1 Considerations on 3GPP and ETSI MEC alignment in edge application orchestration  .... 34
- 3 Compliance study of Nuvla edge orchestration solution  .... 36
- 3.1 Assessment of compliance with ETSI-MEC  .... 36
- 3.1.1 Definition of a MEC Orchestrator  .... 37
- 3.1.2 Mapping to ETSI MEC Specifications and Reference Points  .... 38
- 3.1.3 Gap Analysis vs. MEC Orchestrator Responsibilities  .... 40
- 3.2 Assessment of compliance with 3GPP  .... 42
- 3.2.1 3GPP Management Architecture and Nuvla Positioning  .... 42
- 3.2.2 Gap Analysis vs. 3GPP Management Responsibilities  .... 44
- 3.3 Conclusions on Nuvla Compliance  .... 46
- 4 Feasibility study on standards compliance  .... 48
- 4.1 Standard compliance strategy and approach .... 48
- 4.2 Target compliance levels and related features  .... 49
- 4.2.1 ETSI MEC standards  .... 49
- 4.2.2 3GPP standards  .... 51
- 4.2.3 Implementation Strategy  .... 52
- 5 Enhancements to Nuvla edge orchestration for standards compliance  .... 53
- 5.1 Design of the Nuvla edge orchestration enhancements .... 54
- 5.2 Report on implementation  .... 54
- 6 Conclusions .... 55
- Annex A – ETSI MEC 010-2 APIs – Target levels of compliance .............................................................................................. .... 56
<!-- Page 6 -->

## List of Figures

- Figure 1 Edge computing areas and domains covered by ETSI MEC and 3GPP (ref [RD1]) .................................................... .... 12
- Figure 2 – ETSI MEC Architecture (ref. [RD2]) .......................................................................................................................... .... 13
- Figure 3 – Overview of 3GPP edge computing work (ref. [RD13]) ............................................................................................ .... 19
- Figure 4 – 3GPP Capability Exposure Architecture (ref. [RD17]) .............................................................................................. .... 21
- Figure 5 – CAPIF architecture [RD14] ......................................................................................................................................... .... 22
- Figure 6 – SEAL functional model (on-network) (ref. [RD15]) ..................................................................................................... .... 24
- Figure 7 – EDGEAPP functional model (ref. [RD16]) ................................................................................................................... .... 25
- Figure 8 – Edge Computing Management architecture (ref. [RD19]) ......................................................................................... .... 26
- Figure 9 – GSMA TEC leveraging OPG federation (ref. [RD-10]) .............................................................................................. .... 28
- Figure 10 – ETSI MEC architecture variant with support for SMM (ref. [REF]) .......................................................................... .... 30
- Figure 11 – ETSI MEC and 3GPP EDGEAPP combined architecture (ref. [RD2]) .................................................................... .... 33
- Figure 12 – ETSI MEC and GSMA Operator Platform combined architecture (ref. [RD2]) ........................................................ .... 34
- Figure 13 – 3GPP Edge Computing Management Framework (ref. [RD19]) ............................................................................. .... 35
- Figure 14 - Nuvla in the 5G-EMERGE ecosystem ..................................................................................................................... .... 36
- Figure 15 - MEO main building blocks, from ETSI GS MEC 003 ............................................................................................... .... 37
- Figure 16 – High-level plan of activities for the standard compliance of the orchestration software ......................................... .... 54
- Figure 17 – Timeplan for enhancement of Nuvla edge orchestration solution towards standards compliance .......................... .... 54
## List of Tables

- Table 2.1 Summary contributions of standard bodies and groups involved in edge computing. ................................................ .... 10
- Table 2.2 MEC Architecture components .................................................................................................................................. .... 13
- Table 2.3 MEC Services ............................................................................................................................................................ .... 15
- Table 2.4 MEC Interfaces .......................................................................................................................................................... .... 15
- Table 2.5 Summary of relevant MEC specifications .................................................................................................................. .... 17
Table 6 - Overview of resources and methods of MEO's application package management on Mm1 - Implementation in Nuvla 56 Table 7 - Overview of resources and methods of MEO's/MEAO's application life cycle management on Mm1 - Implementation level in Nuvla................................................................................................................................................................................57 Table 8 - Overview of resources and methods of MEO's application package management on Mm3 - Implementation level in

- Nuvla ......................................................................................................................................................................................... .... 60
Table 9 - Overview of resources and methods of MEPM's application life cycle management on Mm3 - Implementation level in

- Nuvla ......................................................................................................................................................................................... .... 61
Table 10 - Overview of resources and methods of MEO's application life cycle management on Mm3 - implementation level in

- Nuvla ......................................................................................................................................................................................... .... 64
Table 11 - Overview of resources and methods of MEPM-V's application life cycle management on Mm3 – implementation level in Nuvla........................................................................................................................................................................................65

<!-- Page 7 -->

## List of Applicable and Reference Documents

Ref. Reference and applicable documents RD1 ETSI White Paper #36, “Harmonizing standards for edge computing A synergized architecture leveraging ETSI ISG MEC and 3GPP specifications”, July 2020. RD2 ETSI GS MEC 003, “Multi-access Edge Computing (MEC); Framework and Reference Architecture”, v4.1.1, May 2025. RD3 ETSI GS MEC 012, “Multi-access Edge Computing (MEC); Radio Network Information API”, v2.2.1, February 2022. RD4 ETSI GS MEC 015, “Multi-access Edge Computing (MEC); Traffic Management APIs”, v3.1.1, April 2024. RD5 ETSI GS MEC 021, “Multi-access Edge Computing (MEC); Application Mobility Service API”, February

2024.
RD6 ETSI GS MEC 014, “Multi-access Edge Computing (MEC); UE Identity API”, v3.2.1, February 2024. RD7 ETSI GS MEC 010-2, “Multi-access Edge Computing (MEC); MEC Management; Part 2: Application lifecycle, rules and requirements management”, v3.1.1, June 2023. RD8 ETSI GS MEC 011, “Multi-access Edge Computing (MEC); Edge Platform Application Enablement”, v4.1.1, May 2025. RD9 ETSI GS MEC 013, “Multi-access Edge Computing (MEC); Location API”, v3.2.1, November 2024 RD10 ETSI GS MEC 021, “Multi-access Edge Computing (MEC); Application Mobility Service API”, v3.1.1, February 2024 RD11 ETSI GS MEC 037, “Multi-access Edge Computing (MEC); Application Package Format and Descriptor Specification”, v3.2.1, April 2024. RD12 ETSI GS MEC 040, “Multi-access Edge Computing (MEC); Federation enablement APIs”, v3.3.1, March

2025.
RD13 3GPP Edge Computing, https://www.3gpp.org/technologies/edge-computing. RD14 3GPP TS 23.222, “Common API Framework for 3GPP Northbound API”, v19.5.0, March 2025. RD15 3GPP TS 23.434, “Service Enabler Architecture Layer for Verticals (SEAL); Functional architecture and information flows”, v19.5.0, March 2025. RD16 3GPP TS 23.558, “Architecture for enabling Edge Applications”, v19.5.0, March 2025. RD17 3GPP Capability Exposure Frameworks and APIs, https://www.3gpp.org/technologies/ct3-exp-fr- apis RD18 OpenCAPIF, https://ocf.etsi.org/ RD19 3GPP TS 28.538 “Management and orchestration; Edge Computing Management”, v19.3.0, March

2025.
RD20 GSMA Operator Platform Group, https://www.gsma.com/solutions-and- impact/technologies/networks/operator-platform-hp/ RD21 Telco Edge Cloud Value and Achievements, white paper, https://www.gsma.com/solutions-and- impact/technologies/networks/wp-content/uploads/2022/03/GSMA-TEC-Value-Whitepaper-v13.pdf

<!-- Page 8 -->

Ref. Reference and applicable documents RD22 TM Forum Open Digital Architecture, https://www.tmforum.org/oda/ RD23 TM Forum Gen5 Open APIs program, https://www.tmforum.org/oda/open-apis/ RD24 ETSI GS MEC 062, “Multi-access Edge Computing (MEC); Support for Security Monitoring and Management”, v4.0.4 Draft, December 2025 RD25 ETSI GR MEC 041, “Multi-access Edge Computing (MEC); Study on MEC Security”, v3.1.1, March 2024. RD26 ETSI GS MEC 009, “Multi-access Edge Computing (MEC); General principles, patterns and common aspects of MEC Service APIs”, v4.1.1, May 2025. RD27 3GPP TS 33.558, “Security aspects of enhancement of support for enabling edge applications; Stage 2”, v19.0.0, March 2025 RD28 3GPP TR 28.870, “Study on Enablers for Security Monitoring”, v19.0.0, January 2025.

## List of Acronyms

3GPP Third Generation Partnership Program AF Application Function AN Access Network API Application Programming Interface CAPIF Common API Framework CN Core Network CPU Compute Processing Unit EAS Edge Application Server EEC Edge Enabler Client EES Edge Enabler Server GSMA Global System for Mobile communications Association ID Identifier KPI Key Performance Indicator LCM Lifecycle Management M&O Management and Orchestration MEC Multiaccess Edge Computing MEO MEC Orchestrator MEP MEC Platform MNO Mobile Network Operator MOI Managed Object Instance NEST Network Slice Template NFV Network Function Virtualization NPN Non Public Network NRM Network Resource Model NSMF Network Slice Management Function NSSMF Network Slice Subnet Management Function NWDAF Network Data Analytics Function OS Operating System QoD Quality on Demand REST Representation State Transfer SEAL Service Enabler Architecture Layer for Verticals SMM Security Monitoring and Management SMMF Security Monitoring and Management Function SNO Satellite Network Operator SUPI Subscription Permanent Identifier

<!-- Page 9 -->

TMF TeleManagement Forum TN Transport Network TVR Testing, Validation and Results UE User Equipment UPF User Plane Function WP Work Package

<!-- Page 10 -->

## 1 Introduction

In this chapter, the deliverable’s objective and structure are briefly presented and discussed.

### 1.1 Objective of the Document

The main objectives of this deliverable are:

- To document the technology watch performed to identify relevant edge
computing and edge management standard specifications produced by ETSI MEC, 3GPP and relevant industry forums

- To assess the compliance of the Nuvla edge orchestration solution against
the identified relevant edge computing standards, with main focus on ETSI MEC and 3GPP

- To report on the feasibility study on standards compliance of the Nuvla edge
orchestration solution and identify the target compliance level and related features to be implemented

- To document the extensions implemented on top of the Nuvla edge
orchestration software to reach the targeted level of standards compliance (to be included in the next versions of the document)

### 1.2 Structure of the Document

The deliverable follows the structure briefly described below:

- Section 2 focuses on the identification and summary description of relevant
edge computing standards, including ETSI MEC, 3GPP and industry forums such as TMF and GSMA.

- Section 3 focuses on the assessment of the compliance of the Nuvla edge
orchestration solution with edge computing standards.

- Section 4 reports the feasibility study on standards compliance of the Nuvla
edge orchestration software.

- Section 5 focuses on the enhancements to be applied to the Nuvla edge
orchestration software to improve its standards compliance capabilities.

### 1.3 Relation to Other Work Packages in 5G-EMERGE

This document serves as input for the architecture specification in the SAI document, under preparation in WP1, as part of the specification of the Service Functions in the area of edge network and service orchestration functionalities. Moreover, as part of the technology watch and compliance assessment activities reported, this document provides inputs for the design and implementation of the

<!-- Page 11 -->

management and orchestration system for 5G Non Public Networks in edge environments in WP2.5.1.

<!-- Page 12 -->

## 2 Technology watch on relevant edge computing

standards The deployment of edge cloud infrastructures within mobile networks is currently being addressed by multiple standardization bodies. This effort accommodates a variety of business and operational models, ranging from those led by Mobile Network Operators (MNOs) offering edge-hosted cloud services, to collaborative models involving cloud service providers working under agreements with operators. Although these standards differ in scope and maturity, they often complement each another, enabling a strong technological ecosystem for edge computing integration in mobile networks. Two main standardization bodies are leading these activities, ETSI Industry Specification Group (ISG) Multi-access Edge Computing (MEC) and 3rd Generation Partnership Project (3GPP). ETSI MEC defines a modular and open service environment for edge application deployments, while 3GPP, as a complement to the specification of 5G, 5G advanced, and soon 6G networks standards, defines mechanisms and architectural components for integrating edge capabilities within mobile network systems. Figure 1 shows the scope of edge computing areas covered by ETSI MEC and 3GPP. In the horizontal view, both the 3GPP network and application enablement layers include functionality to support the exposure of capabilities within that layer to the layer above. In the vertical view, the various relevant domains and functionalities are reported (device and network domains, management/orchestration and federation capabilities). It is worth to mention that, on top of the Standard Development Organizations (SDOs), several opensource initiatives provide reference practical implementations of standard specifications (becoming de-facto standard per se). Table 2.1 Summary contributions of standard bodies and groups involved in edge computing. SDO Contribution ETSI MEC ETSI MEC has created a common application enablement framework for delivering multi-access edge applications and services. It defines services and APIs for accessing real-time network data (e.g., location, bandwidth, traffic), enabling context- aware application behavior. The MEC architecture also addresses orchestration, mobility support, and service discovery. Moreover, ETSI MEC is extending its scope to support federation models, where MEC applications and services can interoperate across multiple operators, vendors, geographical regions. 3GPP SA6 has developed the EDGEAPP architecture, which facilitates interaction between edge-hosted applications and user devices. This includes support for Edge Enabler Clients (EEC) on end

<!-- Page 13 -->

devices and Edge Enabler Servers (EES) on the edge infrastructure, to deploy and consume edge applications on Edge Application Servers (EAS). SA6 also defines CAPIF (Common API Framework) as a standardized method for exposing and accessing APIs within the edge ecosystem. SA2, which defines the 5G core architecture, ensures that network functions can support edge-aware service placement. It defines how user data flows can be optimally steered to edge application servers. SA5 contributes by defining the management and charging architecture for edge applications. This includes the lifecycle operations (deployment, monitoring, deletion) and accounting mechanisms for services running in edge environments. In addition to ETSI MEC and 3GPP, relevant industry forums such as GSMA and TM Forum develop specifications and recommendations to realize a transversal view of all capabilities required for building network, service and edge application management and operation platforms, enabling further market uptake of edge computing commercial services. The following sub-sections provide an overview of ETSI MEC and 3GPP edge specifications, architectures and solutions, together with a brief summary of GSMA and TM Forum ongoing activities, as a result of the technology watch performed on relevant edge computing and edge management standards.

<!-- Page 14 -->

Figure 1 Edge computing areas and domains covered by ETSI MEC and 3GPP (ref [RD1])

### 2.1 ETSI MEC

The goal of ETSI MEC is to create a standardized, open environment to allow the efficient and seamless integration of applications from vendors, service providers, and third-parties across multi-vendor Multi-access Edge Computing platforms. The ETSI MEC activity aims at filling the gaps between the telco and IT-cloud worlds, providing IT and cloud-computing capabilities within the Radio Access Network (RAN) and specifying the elements that are required to enable applications to be hosted in a multi-vendor multi-access edge computing environment. In practice, ETSI MEC offers application developers and content providers a wide set of cloud- computing capabilities and an IT service environment at the edge of the network. This environment targets ultra-low latency and high bandwidth as well as real-time access to radio network information that can be leveraged by applications. ETSI MEC lays its foundations in the evolution of mobile base stations in the 5G era and in the convergence of IT and telecommunications infrastructures. In this context, ETSI MEC enables new vertical business segments and services for consumers and enterprise customers.

<!-- Page 15 -->

From a practical perspective, ETSI MEC defines a common application enablement framework for delivering and managing applications and supporting services in multi-access edge networks (including 5G, 4G, Wi-Fi and fixed access). The ETSI MEC architecture, defined in the ETSI GS MEC 003 [RD2], is shown in Figure 2 as a two- level architecture. The MEC system level includes the MEC system management components (MEC orchestrator and Operations Support System), as well as the elements to interface with the end-user device applications (i.e. the User app proxy). On the other hand, the MEC host level include the components deployed and executed in a given MEC host (i.e. the MEC platform, the MEC applications (MEC apps) and the virtualization infrastructure), as well as the related management elements (MEC platform manager and virtual infrastructure manager). Figure 2 – ETSI MEC Architecture (ref. [RD2]) Table 2.2 below provides a brief summary description of the various MEC architecture components. Table 2.2 MEC Architecture components MEC component Description MEC Host Infrastructure element that provides the virtual resource for instantiating the MEC Apps.

<!-- Page 16 -->

MEC Platform Provides the discovery and advertising mechanisms that (MEP) enables MEC Apps in offering and consuming MEC Services according to the “Service-Based Architecture” paradigm. Offers functionalities for traffic routing from/towards MEC Apps and Services, 3GPP access networks, local and external networks and a DNS service MEC Platform Implements management functionalities related to the Manager (MEPM) MEC Apps’ lifecycle, interacting with the MEC orchestrator. Handles the configuration of the DNS and traffic rules on the MEC Platform during instantiation and termination phases (eventually solving conflicts due to concurrent traffic flows). Virtual Handles the allocation of compute, storage and network Infrastructure virtual resources for instantiating MEC Apps. Manager Implements monitoring functionalities on top of such resources, covering both performance fault management. Support and manage specific advanced computing functionalities (acceleration, CPU pinning, etc.) MEC Orchestrator Orchestrates the entire MEC System, managing the (MEO) different MEC hosts and the offered MEC services. Coordinates the on-boarding, validation, instantiation, termination and migration procedures of MEC Apps, selecting the target MEC hosts on the base of their functional and performance requirements. User Application Allows user’s applications that run on mobile devices to Lifecycle request the on-boarding, instantiation and termination of Management applications in the MEC System and to obtain information Proxy about their status. The MEC apps can be considered as software applications, developed by either the operator or third parties, that are deployed and executed in a MEC host and utilize MEC services and APIs to deliver edge-enhanced functionality. As depicted in the MEC architecture figure, the MEC platform offers and implements a set of added value services (named MEC services) to MEC apps that allow to retrieve critical information from the MEC system (e.g., real time network and context info, location awareness, etc.) as well as to influence the behavior of the MEC system and the

<!-- Page 17 -->

underlying network (e.g. for traffic and bandwidth management, etc.). Table 2.3 below provides a brief summary description of the main relevant MEC services. Table 2.3 MEC Services MEC Service Description Radio Network Service to request information related to radio access Information networks, specified in ETSI GS MEC 012 [RD3] Location Service to request general localization information or related to a specific UE, specified in ETSI GS MEC 013 [RD4] Traffic Steering Service to request the activation and de-activation of traffic rules associated to a specific UE in the MEC Platform, specified in ETSI GS MEC 015 [RD5] Bandwidth Service to request the bandwidth allocation, dedicated to Management specific traffic flows in support to one or more MEC apps, specified in ETSI GS MEC 015 [RD5] Application Service to support applications’ mobility, with re-location Mobility mechanisms and users’ application context migration, specified in ETSI GS MEC 021 [RD6] UE Identity Service to request access to identifiers such as IMSI, IP address, or cell ID of Ues for contextual awareness, specified in ETSI GS MEC 014 [RD7] To regulate the interactions among the various architecture components, ETSI MEC defines a set of interfaces, categorized in three different types:

- Interfaces dealing with the MEC platform functionalities (reference points
Mp in Figure 2);

- Management interfaces (reference point Mm in Figure 2);
- Interfaces with external elements (reference point Mx in Figure 2)
Table 2.4 MEC Interfaces MEC Interface Components Description Mm1 OSS – MEO MEC Apps instantiation and termination requests. Mm2 OSS – MEPM MEC Platform configuration requests.

<!-- Page 18 -->

Monitoring information management, at functional and performance level. Mm3 MEO – MEPM Lifecycle management of MEC Apps. Management of MEC Services offered by the platform. Mm4 MEO – VIM Management of available virtual resources in the MEC Host. Management of MEC Apps’ software images Mm5 MEPM – MEP MEC Platform configuration. Traffic rules configuration between MEC Apps. Management of re-location procedures of MEC Apps Mm6 MEPM – VIM Allocation and de-allocation of virtual resources for instantiate and terminate MEC Apps Mm7 VIM – NFVI Management of the virtual infrastructure Mm8 User Application MEC Apps related requests (query, LCM Proxy – OSS instantiation and termination) coming from applications on mobile devices Mm9 User Application MEC Apps instantiation and termination LCM Proxy – MEO requests from applications running on mobile devices Mp1 MEP – MEC Apps MEC Services’ discovery and registration mechanisms offered by MEC Apps. Traffic rules activation from/to/between MEC Apps and DNS rules. Additional MEC Apps services (e.g., storage for data persistency, NTP service, etc.) Mp2 MEP – Virtualized Data-plane configuration for traffic infrastructure data- routing between MEC Apps and MEC services, external networks.

<!-- Page 19 -->

plane on MEC host (NFVI) Mp3 MEP belonging to Control communications between MEPs different MEC hosts in distributed environments. Such communications may happen within a single MEC System or between MEPs in MEC Hosts belonging to different MEC Systems, e.g., in support to mobility management. For a comprehensive technology watch of ETSI MEC, which represents the main target for standard compliance of the edge orchestration solution in WP2.3.2, the following Table 2.5 provides a summary description of relevant ETSI MEC specifications for compliance considerations. Table 2.5 Summary of relevant MEC specifications MEC Specification Description ETSI GS MEC 003 Specifies the overall MEC framework and reference architecture, including all functional components, interfaces, and roles. It serves as the blueprint for MEC deployments and ensures consistency across vendors. It analyses relationships and touching points with other standards and industry specifications (3GPP, GSMA, CAMARA). ETSI GS MEC 010-2 Specifies the lifecycle management approach for MEC applications, including information models for MEC apps and procedures and APIs for onboarding, instantiating, terminating, and updating MEC apps. It serves as main reference for the implementation of MEC orchestrators and MEC platform managers. ETSI GS MEC 011 Defines the management aspects of MEC platforms and MEC services, specifying interfaces and APIs. It regulates the interactions between MEC app instances and the MEC Platform for consumption of MEC services. ETSI GS MEC 012 Specifies the Radio Network Information API, which is exposed by the related MEC service to enable MEC apps to query and subscribe to radio metrics and status. It is used

<!-- Page 20 -->

by MEC apps to adapt their behavior based on radio signal strength or congestion. ETSI GS MEC 013 Specifies the Location Service API, which is exposed by the related MEC service to enable MEC apps to obtain real-time and historical location data of Ues and devices. It can be used to enable geofencing, navigation, and context-aware edge services. ETSI GS MEC 014 Specifies the UE Identity API, which is exposed by the related MEC service to allow MEC apps to retrieve identification information (IP, IMSI, SUPI, etc.) related to the Ues. It can be used to improve identification and correlation across edge services. ETSI GS MEC 015 Specifies the Bandwidth Management and Traffic Steering APIs, which are exposed by the related MEC services to enable MEC apps to allocate, manage, and monitor bandwidth usage per flow or UE, and enable the traffic redirection in support of MEC breakouts. ETSI GS MEC 021 Specifies the Application Mobility Service API, which is exposed by the related MEC service to manage MEC apps handover across MEC hosts, considering application state and traffic migration. ETSI GS MEC 037 Specifies the data model of the MEC app descriptor, and the format and structure of the MEC app package file to be onboarded in the MEC system through the MEC orchestrator. ETSI GS MEC 040 Specifies the functionalities to support federation between MEC systems from different owners. It describes the information flows, operations, data models and APIs to enable management of MEC apps across MEC systems. It also considers and maps to relevant work of other standards (e.g. GSMA).

### 2.2 3GPP

3GPP is the reference SDO that creates and maintains the technical specifications for mobile networks. Its mission is to develop global standards for mobile communications, including 5G and 6G. 3GPP ensures interoperability and compatibility between different mobile networks, operators, and devices manufacturers, and specifies architectures, protocols and interfaces for radio

<!-- Page 21 -->

access, core network and service capabilities. In this context, edge computing has been a major focus starting from 3GPP Release 17 as part of the 5G network specification and evolution, with four key Technical Specification Group (TSG) Service and System Aspects (SA) carrying out related studies and normative work, in particular:

- SA2, focusing on system architecture enhancement for supporting edge
computing.

- SA3, focusing on security aspects for supporting SA2 and SA6 architectures.
- SA5, focusing on management and charging aspects on edge computing.
- SA6, working on the edge enabler layer architecture, and deployment
scenarios Figure 3 presents an overview of the edge computing technical areas and related aspects covered by 3GPP. The application layer is responsible to consume 3GPP specified edge computing capabilities. The edge enabler layer (linked to SA6) includes the overall functionalities provided by the enabler entities in support of the consumer edge applications. The edge hosting environment enables the execution of edge applications in dedicated edge application servers, while the edge management layer (linked to SA5) focuses on management of the overall 3GPP edge system, including network, enabling platforms and applications. The 3GPP transport layer (linked to SA2) includes the functionalities provided by the 3GPP networks in support, mostly at the user plane and linked to different edge connectivity options (e.g. distributed anchor points, session breakouts), of the consumer applications and edge enabler entities. Figure 3 – Overview of 3GPP edge computing work (ref. [RD13]) In the context of the 5G-EMERGE work on edge network and service orchestration carried out in WP2.3, the 3GPP SA6 work on application-enabling service frameworks (also responsible for vertical enablers) provides highly relevant

<!-- Page 22 -->

specifications to facilitate edge-cloud deployments. In particular, 3GPP SA6 is continuously evolving the following frameworks and specifications:

- Common API Framework for 3GPP Northbound APIs (CAPIF), in 3GPP TS
23.222 [RD14], which provide a common architecture, mechanisms and APIs to securely expose 5G Northbound APIs (i.e. 5G Core APIs) to external consumers.

- Service Enabler Architecture Layer for Verticals (SEAL), in 3GPP TS 23.434
[RD15], which provides a functional architecture to support vertical applications at the edge by specifying common application plane and signaling plane entities and services that can be shared across vertical applications.

- Architecture for Enabling Edge Applications (EDGEAPP), in 3GPP TS 23.558
[RD16], which builds upon the concepts set by CAPIF and SEAL and describes the 3GPP enabling layer and application architecture to implement and manage edge applications on the 5G Edge Data Network (EDN). Rather than considering them as independent frameworks, CAPIF, SEAL and EDGEAPP, they all contribute to realize the 3GPP capability exposure architecture, as indicated in Figure 4. Indeed, the Network Northbound APIs are offered by Service Capability Exposure Function (SCEF) in 4G and Network Exposure Function (NEF) in 5G, to securely expose core network capabilities (network events, QoS, traffic influence, network analytics, etc.) to external applications. The Application Enabler Layer APIs provide abstraction of the 5G network capabilities for specific and common vertical applications, while offering enablement and common core services catering to various industry vertical applications. It provides these abstractions either in parallel or on top of the abstraction functionalities already provided by Network Northbound APIs, and examples of such enabler frameworks and APIs include indeed SEAL and EDGEAPP. As a transversal common framework, CAPIF allows for discovery of and secure access to network and application enabler layer APIs, with the possibility to support and integrate non-3GPP defined APIs (such as ETSI MEC, TM Forum, CAMARA).

<!-- Page 23 -->

Figure 4 – 3GPP Capability Exposure Architecture (ref. [RD17])

#### 2.2.1 CAPIF

The CAPIF, specified in 3GPP TS 23.222 [RD14], provides a common framework with dedicated API management and exposure functionalities that are applicable to 5G network and service APIs (e.g., Northbound APIs, Application Enabler Layer APIs). In particular, 3GPP specifies CAPIF functional model, procedures and information flows for API management. In particular, these include:

- API discovery and invoker onboarding functions for API invokers
- API publishing, management, exposing functions for API providers
- AuthN/AuthZ, security, routing
- Management functions, like reporting, charging, auditing
This allows external applications and services to consume 5G network services invoking their northbound API, e.g., for retrieving network KPIs, localization information, etc. While CAPIF is natively linked to 5G network and services capabilities, it can be used for 3GPP-specified APIs as well as any other set of APIs, e.g., defined by other SDOs (e.g., ETSI MEC).

<!-- Page 24 -->

Figure 5 – CAPIF architecture [RD14] Figure 5 shows the CAPIF architecture, with the CAPIF hosted within the PLMN operator network and the API invoker (which can be embedded in the application) provided by a third-party application provider. The API invoker may reside within the PLMN trust domain or outside. The CAPIF architecture includes different elements. The CAPIF Core Function, which exposes CAPIF APIs toward invokers, and the API Provider Domain Functions, which include: i) the API Exposing Function, exposing Service APIs, which is the provider of the service API and the entry point for the API Invokers; ii) the API Publishing Function, which enables the API provider to publish the service APIs information allowing the discovery of service APIs by the API invoker, iii) the API Management Function, which enables the API provider to perform administration of the service APIs. The CAPIF deployment can be centralized or distributed, with several deployment models, including CAPIF interconnections with multiple CAPIF provider domains. The API Invoker may be either a (virtual) application running in the edge, or an application running in the UE, and it implements several functionalities: i) initiation of API Invoker onboarding and offboarding; ii) authentication and authorization procedures to access service APIs; iii) discovery of service APIs information; iv) consumption of service APIs.

<!-- Page 25 -->

On the CAPIF side, the CAPIF Core Function authenticates and authorizes the API invoker to allow its access to the service APIs, it handles the access control for the service APIs based on operator’s policies and publishes and stores service APIs information, enabling their discovery. It also monitors and handles accounting and charging for service API invocations. The API Exposing Function authenticates and authorizes the API Invoker in collaboration with the CAPIF Core Function, and it logs service APIs invocations at the CAPIF Core Function. The API Publishing Function publishes service API information to the CAPIF Core Function, while the API Management Function takes care of policy configurations and monitoring of service APIs status and events from CAPIF Core Function, also auditing service API invocations. An opensource implementation of the CAPIF framework is also available, in the form of a dedicated ETSI Software Development Group (SDG), called OpenCAPIF [RD18].

#### 2.2.2 SEAL

SEAL provides a service layer on top of 5G networks to support vertical applications at the edge. Its main goal is to ease and fasten the development and deployment of vertical applications, through a set of common auxiliary services to be exposed over 3GPP and CAPIF APIs and that can be reused across different vertical applications. The SEAL architecture, the procedures and information flows, as well as the APIs offered by each service are defined in 3GPP TS 23.434 [RD15]. Figure 6 –shows a deployment with a Vertical Application Layer (VAL) server interacting with a SEAL server placed in the 3GPP network (on-network functional model). However, it should be noted that several deployment models are possible: for example, the VAL server can interact with multiple SEAL servers from different network operators’ domains, or different SEAL servers can interact together, within single network operator domains or among different network operators. Moreover, the SEAL servers can be also deployed in the VAL service provider domain and interact with the 5G network system of one or more network operators. Finally, SEAL servers can be also deployed outside of both the VAL service provider domain and network operator domain. This is the case when the SEAL is operated by a third actor, i.e., the SEAL provider, with SLAs in place with both VAL service providers and network operators. In practice, SEAL offers the following common services towards the VAL:

- Location management
- Group management
- Configuration management
<!-- Page 26 -->

- Key management
- Identity management
- Network resource management
- Data delivery
- Notification management
- Network slice capability enablement
- Application data analytics enablement
Figure 6 – SEAL functional model (on-network) (ref. [RD15])

#### 2.2.3 EDGEAPP

The 3GPP EDGEAPP framework, specified in 3GPP TS 23.558 [RD16], builds upon the concepts set by CAPIF and SEAL and provides the 3GPP enabling layer and application architecture to implement and manage edge applications on the 5G Edge Data Network (EDN). In particular, EDGEAPP defines the exposure of northbound APIs towards edge applications, offering capabilities for service provisioning, rich application discovery and service continuity. It enables execution and consumption of edge applications over 3GPP networks, and for this it provides an abstraction layer on top of the abstraction provided by the 3GPP (CAPIF enabled) 5G Northbound APIs. EDGEAPP allows application developers to locate, connect, and switch to the most suitable application server on the edge network, and consequently to exploit the potential of the underlying 3GPP network in optimizing the application and service performances. Figure 7 shows the EDGEAPP functional model, which is composed by the following elements:

S V E A A L L S V E A A L L c U c l i l E e i e n S n t E t ( s A ( s ) L ) - C S V E A A L L - U - U U U n s 3e y Gts w t P e o P m r k N e t w o r k i n t e r f a c e s S V E A A L L s s e e r v r S v e E e r A r ( s L ( s ) - ) S

<!-- Page 27 -->

- Application Clients (Acs) or Edge Application Clients (EACs), which represent
the application embedded in the UE behaving as the EDGEAPP client function. Most of the Acs are out of scope for 3GPP, except in the case of procedures related to service continuity (for application context relocation).

- Edge Application Servers (EASs) which is the application server in the edge
performing the server functions and hosting the actual edge application. The AC connects to the EAS to benefit and consume applications running at the edge.

- Edge Enabler Server (EES), which is primarily responsible for enabling
discovery of the EASs available in the network for the AC to consume.

- Edge Enabler Client (EEC), which provides support functions, such as EAS
discovery to the Acs in the UE (or the device).

- Edge Configuration Server (ECS), which provides configurations to the EEC
(and consequently to the AC) to connect with an EAS. Figure 7 – EDGEAPP functional model (ref. [RD16]) On top of this EDGEAPP architecture, 3GPP also specifies an Edge Computing Management architecture in 3GPP TS 28.538 [RD19], which focuses on lifecycle management aspects of EDGEAPP components and elements (EAS, EES, ECS), and edge applications. In particular, it covers lifecycle management, performance assurance, fault supervision. As depicted in Figure 8, the Edge Computing Management architecture considers three types of stakeholders:

- Edge Computing Service Providers (ECSP), responsible for the creation of
EASs and Acs.

- Application Service Providers (ASP), responsible for the deployment of EDN
that contains EAS and EES.

<!-- Page 28 -->

- PLMN operators, responsible for the deployment of 5G network functions.
In practice, the 3GPP management system framework in the EDGEAPP architecture is composed by the combination of the PLMN management system and the ECSP management system. Both of them can in turn leverage ETSI Network Function Virtualisation (NFV) Management and Orchestration (MANO) platform to deploy and operate the various elements as VNFs. Figure 8 – Edge Computing Management architecture (ref. [RD19]) Moreover, the 3GPP TS 28.538 also defines the Edge Network Resource Model (NRM), which provides a standard, object-oriented information model of EDGEAPP entities, managed objects, and their relationships, to enable automated management, configuration and orchestration of EDGEAPP functions and services. It is used for creating and managing Managed Object Instances (MOIs), which represent the manageable assets within the 3GPP edge computing environment. In particular, the Edge NRM enables the configuration and management of entities within the Edge Data Network, i.e. the EAS, EES and ECS. On top of the Edge NRM, 3GPP TS 28.538 also specifies a set of Management Services (MnS) for edge computing, which cover provisioning (i.e. lifecycle management) and performance assurance capabilities for the managed entities (i.e. EAS, EES and ECS).

### 2.3 Industry Forums

#### 2.3.1 GSMA

GSMA is a global-led organization within the mobile industry, including 750 mobile operators and over 400 companies in the broader mobile ecosystem. It aims to drive initiatives shaping the future of mobile communications in the area of 5G, IoT AI, and Security.

<!-- Page 29 -->

Within its Future Networks thread, there are two initiatives which are relevant to edge-cloud computing applicability in the telecommunications industry: the Operator Platform Group (OPG) and the Telco Edge Cloud (TEC) Group. The GSMA OPG [RD20] aims to support operators to monetize their footprint, their existing relationships with enterprises, and their competence to provide high- reliability services. The OPG defines a common Operator Platform that exposes operator services and capabilities to customers and developers, fostering the creation of an open and wide edge-ecosystem that brings together operators, platform developers, edge cloud providers, SDOs, Open-Source Projects. It targets the creation of an architecture and technical requirements to guide other SDOs in the development of specifications, focusing in the first phase on edge capabilities and services. While the OPG focuses on technical requirements and development of the operator platform architecture, a special subgroup, the Operator Platform API Group (OPAG) fosters and promotes the alignment and collaboration with other SDOs and initiatives (in particular 3GPP, ETSI MEC, Linux Foundation CAMARA) in API definitions fulfilling OPG requirements. In particular, the OPG considers the enhancement of the edge capabilities with:

- Smart edge selection, allocation and provisioning to perform application
deployment and access from the closest edge.

- Edge federation to offer a multi domain access to customers and enhance
edge service under roaming scenarios.

- Tight network integration to enhance mobility and user experience.
On the other hand, the TEC Group [RD21] has been launched in GSMA in March 2020 by 19 operators, and currently counts 25 members, with the aim to design and develop a global edge computing service based on the federation of edge infrastructures and platforms of multiple operators and edge service providers. TEC has a prominent commercial focus and enables service providers to develop and deploy their solutions over a global telco footprint from a common interface, exploiting all the benefits of a distributed telco infrastructure, made available across different, federated operator networks. In this context, TEC is aiming to align with ETSI MEC (in light of MEC business models, charging principles and commercial deployment considerations), and has primary focus on edge cloud trials and POCs [RD21]. However, the OPG and TEC activities are closely related. Indeed, as depicted in Figure 9, TEC incorporates the Operator Platform from OPG to integrate multiple edges from different providers in support of mobility and share of resources, towards an interoperable, flexible and federated edge framework.

<!-- Page 30 -->

Figure 9 – GSMA TEC leveraging OPG federation (ref. [RD-10])

#### 2.3.2 TM Forum

TM Forum is a global industry association that drives digital transformation in the telecommunications sector. It provides a collaborative platform for industry professionals to share best practices, develop standards, and drive innovation in areas such as 5G, Internet of Things (IoT), cloud computing, and customer experience management. Strategic collaboration of TM Forum with GSMA builds upon the realization that GSMA’s focus on mobile networks and TM Forum’s efforts in IT, data and AI make the collaboration a perfect fit to address the challenges in edge applications operation. TM Forum has developed a comprehensive suite of specifications to guide Communication Service Providers (CSPs) which are relevant to edge computing and edge applications delivery and operation. In particular:

- Open Digital Architecture [RD22], a modern, cloud-native, and modular
architecture designed to help CSPs and enterprises transition from legacy, monolithic telecom systems to agile, AI-driven, and API-first digital platforms. In particular, ODA enables faster service innovation, improved automation, and seamless integration of AI/ML, 5G, and cloud technologies.

- Gen5 Open APIs program [RD23], which provides a comprehensive set of
OpenAPI specifications that aim at further simplifying customer, product, service and resource APIs use in vertical specific applications, with support for latest event-driven and AI-based edge architectures and intent-based automation. This allows to develop open autonomous network and cloud infrastructures ready to support pervasive and distributed edge applications.

<!-- Page 31 -->

### 2.4 Security Aspects

Security is a key requirement for edge infrastructures, services and applications, in particular in multi-vendor scenarios when edge capabilities and services are offered and exposed to third-party applications. In the context of edge computing standardization, both ETSI MEC and 3GPP tackle security aspects, even if with different strategies and focusing on partially aligned aspects. However, while security per-se is a broad technical area, that when applied to edge computing in 5G-EMERGE it is addressed specifically in WP2.2, this document and the entire work in WP2.3.2 should focus on aspect related to edge management and orchestration. Therefore, the following sub-sections focuses on providing an overview of the relevant ETSI MEC and 3GPP specifications in the area of edge management and orchestration security.

#### 2.4.1 ETSI MEC

ETSI MEC tackle security aspects from different angles, focusing on ensuring a trusted and secure environment for applications running at the edge, and defining mechanisms to secure the MEC APIs exposed to them. In addition, ETSI MEC has recently introduced a variant of the generic MEC architecture to enable support for Security Monitoring and Management (SMM) in the MEC system [RD2]. As depicted in Figure 10, a dedicated SMM Function (SMMF) is introduced to provide a transversal and comprehensive functionality of security monitoring and management to continually assess and preserve the security of edge applications and deployments, supporting the various components and building blocks of the MEC architecture.

<!-- Page 32 -->

Figure 10 – ETSI MEC architecture variant with support for SMM (ref. [REF]) In particular, the SMM capabilities are defined as optional in the MEC system, and aims through the SMMF at collecting security-related data and generate security alerts to indicate potential security issues in the MEC system. For this the SMMF can interface with the MEO, MEP, MEPM, Vim and OSS to retrieve security-related data for analysing and detecting potential attacks. This security variant of the ETSI MEC architecture allows SMM functionalities to be provided by either external SMMFs (e.g. already existing and implemented by the network operator or edge infrastructure provider) or MEC-related SMMFs. On top of this high-level functional description provided with ETSI MEC 003 [RD2], a more detailed and in-depth definition of SMMF features is currently available in ETSI MEC 062 [RD24], which is work in progress specification, in Draft status at the time of writing. This specification is built on top of the ETSI MEC 041 [RD25], (which as Group Study introduces the MEC security landscape, and identifies key issues security challenges in the MEC system), and further defines the main operations, security entities and events managed by the SMMF. In particular, these are:

- Security Alerts, which are notifications of security events occurred in the MEC
system. These are generated by the SMMF based on the analysis of collected monitoring data.

- Security Directives: which are defined as the set of security actions a given
MEC entity can implement in response to a security alert notification.

- Security Profiles, which define the type of security related data a MEC entity
can expose to the SMMF. They also define the security directives a given MEC entity can implement and how.

- Control Policies, which determine the security actions to be taken (i.e.,
security directive) by MEC entities in response to security alerts.

- Monitoring Policies: A monitoring policy specifies parameters such as data
collection, analysis, and alerts. On top of these, ETSI MEC 062 also defines high level procedures (in the form of workflows) to support the security monitoring and management, together with an information model to regulate the information to be shared with the SMMF (for alerts, directives, policies, profiles and monitoring). However, it does not include definitions of SMMF related APIs and data models, keeping the current specification at an architectural and functional level. Beyond this security management related architectural variant, ETSI MEC also defines other more generic security mechanisms to be enforced in the MEC system, and which mostly refer to API security and MEC application integrity and authenticity. In particular, for what concerns the API security, ETSI MEC 009

<!-- Page 33 -->

imposes the use of OAuth 2.0 for authentication and authorization of MEC APIs, including those exposed by the MEC orchestrator for MEC application lifecycle management [RD26]. On the other hand, for MEC application integrity, the security of the application onboarding process is key to prevent malicious software to be executed in the MEC system. In this context, ETSI MEC 037 [RD11] defines the specific format and structure of the MEC Application Package, which serves as the container for the software image and the application metadata. To ensure integrity and authenticity, ETSI MEC 037 requires to include as part of the package specific security artifacts in the form of digital signature or a digest (hash) of the package content, to verify the package origin and content. This allows, during the onboarding phase (as mandated by ETSI MEC 010-2 [RD7]), the MEC orchestrator to perform: i) integrity checks to identify any data corruption or alteration, ii) authenticity checks to verify the digital signature and prevent the deployment of untrusted, unauthorized or rogue applications.

#### 2.4.2 3GPP

3GPP SA3 is responsible for the study and specification of the overall security and privacy aspects within the 3GPP system. In particular, its scope is naturally very wide, and include analysis of threats, identification of security requirements, and specification of security solutions, architectures and protocols to protect the 3GPP network infrastructure and the user data. In the context of edge computing and edge management, 3GPP SA3 serves as support for SA2 (system architecture) and SA5 (management and orchestration) for identifying critical security features. Despite the heterogeneity of the 3GPP SA3 work, the availability of studies and specifications directly impacting the edge management and orchestration architecture covered in this document (and in WP2.3.2) is limited, and practically focuses on the identification of basic security mechanisms for enabling edge applications and on enablers for security alarm management. The 3GPP TS 33.558 [RD27] focuses on security aspects and requirements of the EDGEAPP architecture interfaces, as well as on authentication among EDGEAPP functions. Specifically, it defines the mechanisms for the protection of interfaces among the EEC, EES, and EAS components (thus focusing on EDGE-1 and EDGE-3 interfaces), and mandates the use of TLS to ensure confidentiality and integrity in data exchange over untrusted transport and backhaul networks. It also specifies the AuthN/AuthZ mechanisms to supported in the EDGEAPP architecture, selecting OAuth 2.0 as the solution to ensure that only registered and authorized EAS instances can consume EDGEAPP services. In addition, the 3GPP TS 33.358 also defines secure token-based mechanisms to retrieve through the 5G NEF and make use of 5G system User Equipment (UEs) identifiers, without exposing sensitive UE

<!-- Page 34 -->

information (e.g. the SUPI), still allowing edge applications and EAS instances to identify 5G UEs. On the other hand, in the context of SA5, with Release 19 3GPP started to study on technical enablers for security monitoring, identifying in 3GPP TR 28.870 [RD28] in the latest release a limited set of use cases focusing on security aspects in the management plane, and specifically targeting fault management capabilities and security alarm filtering, reporting and retention policies to be adopted into security management functions.

### 2.5 Standardization landscape harmonization

Despite the various specifications and edge enabling frameworks produced by relevant SDOs (i.e. ETSI MEC and 3GPP) and industry forums (e.g., GSMA), a harmonization process of this complex standardization landscape is already happening. In particular, ETSI MEC is continuously promoting the compatibility and mapping of its architecture and edge approach with 3GPP EDGEAPP and GSMA Operator Platform architectures, as a mean to realize a synergy towards a harmonized edge computing architecture, to be used as a blueprint for edge/cloud deployments in the telco domain. As depicted in Figure 10, 3GPP EDGEAPP can be used for 5G access specific interactions and interfaces, while ETSI MEC can be used for the edge management and orchestration functionalities to deploy and operate EAS and MEC apps, as well as EES and MEC platforms, which are considered complementary functional blocks by 3GPP and ETSI MEC. Indeed, despite of the obvious differences between the two architectures architecture, the EES and MEC platform provides similar functionalities (and can be collocated in same edge location). Similarly, the EAS and MEC apps can be associated to the implementation of actual edge applications, and can be also collocated in the same edge locations. This means that EDGEAPP and ETSI MEC reference points and interfaces can be combined, assuming that edge implementations may be: i) compliant with the 3GPP EDGEAPP architecture; ii) compliant with the ETSI MEC architecture; iii) compliant with both sets of specifications. This last option enables a truly unified edge approach and can avoid potential duplication of separately deploying EESs and MEC platforms, and EASs and MEC apps.

<!-- Page 35 -->

Figure 11 – ETSI MEC and 3GPP EDGEAPP combined architecture (ref. [RD2]) On the GSMA side, both ETSI MEC and GSMA OPG have worked on the alignment and mapping between the GSMA Operator Platform architecture and the ETSI MEC framework. As shown in Figure 11, this alignment exercise brought to the mapping of GSMA Operator Platform interfaces with ETSI MEC reference points. In particular, the GSMA Operator Platform E/WBI (east west bound interface), allows to interconnect peering Operator Platform instances, with the primary goal of allowing Application Providers of an Operator Platform to access and use edge cloud resources and services of another Operator Platform. This interface is not directly exposed to the Application Providers and is primarily managed by a Federation/Hub Manager Role functionality in the Operator Platform. For this, such interface can be mapped and implemented through the MEC Federation APIs (defined in [RD12]), which allows to manage the deployment and operation of MEC apps and MEC services across MEC systems owned by different operators.

<!-- Page 36 -->

Figure 12 – ETSI MEC and GSMA Operator Platform combined architecture (ref. [RD2])

#### 2.5.1 Considerations on 3GPP and ETSI MEC alignment in

edge application orchestration As anticipated above, ETSI MEC and 3GPP EDGEAPP both provide edge application enablement frameworks, with clear commonalities and differentiating points. However, when considering edge management and orchestration capabilities and functionalities they natively show complementarity and alignment. ETSI MEC defines a common edge application enablement framework for delivering and managing applications and supporting services in multi-access edge networks. Most importantly, it integrates native edge orchestration capabilities and APIs through its MEC Orchestrator component, which is responsible to manage the lifecycle of MEC applications (and MEC services) to be deployed and operated within the MEC systems. On the other hand, 3GPP EDGEAPP defines a framework to enable 5G UEs to discover, register, and consume edge applications from edge servers. For the edge management and orchestration capabilities, it relies on a dedicated 3GPP Edge Computing Management architecture (TS 28.538 [RD19]) to manage the lifecycle of EAS, EES and ECS instances. In particular, as anticipated in section 2.2.3, the 3GPP Edge Computing Management architecture defines a dedicated management system for the Edge Computing Service Provider (ECSP), i.e. the one offering the 3GPP EDGEAPP functionalities and services, which provide a set of edge computing management capabilities for lifecycle management EDGEAPP entities (to deploy, terminate and modify EAS; EES and ECS instances) and implement the related procedures and workflows defined in [RD19].

<!-- Page 37 -->

Figure 13 – 3GPP Edge Computing Management Framework (ref. [RD19]) Most important, as depicted in Figure 13, 3GPP TS 28.538 delegates to the MEC orchestrator (as an alternative to the ETSI NFV Management and Orchestration – MANO) the fulfilment of the EAS, ECS and EES lifecycle management, specifically indicating the procedures, workflows and APIs for MEC application lifecycle management specified in ETSI MEC 010-2 [RD7] as those that can be adopted to comply with the 3GPP Edge Computing Management Framework. In practice, the compliance with ETSI MEC, and in particular the adoption of ETSI MEC 010-2 procedures and APIs through a MEC Orchestrator guarantees alignment and compliance with 3GPP EDGEAPP for what concerns the management and orchestration aspects.

<!-- Page 38 -->

## 3 Compliance study of Nuvla edge orchestration

solution

### 3.1 Assessment of compliance with ETSI-MEC

Nuvla is an edge-to-cloud management platform, composed of two software pieces: NuvlaEdge, an agent software running on the edge device, and Nuvla.io, a cloud-hosted Platform as a Service which allows users to remotely manage their edge devices and applications. In the 5G-EMERGE ecosystem, Nuvla orchestrates containerized applications at the far edge, and operates as such as ETSI MEC Orchestrator (MEO) in the 5G-EMERGE chain, as described in Figure 14 below. Figure 14 - Nuvla in the 5G-EMERGE ecosystem Assess compliance of Nuvla with the ETSI MEC standard is key in the edge computing ecosystem, to make sure Nuvla can operate seamlessly with other components and easily integrate into complex systems. The following specifications were analysed to conduct this compliance study:

- ETSI GS MEC 003 [RD2] – System-level roles & responsibilities.
- ETSI GS MEC 010-2 [RD7] – Application lifecycle & orchestration flows.
- ETSI GS MEC 011 [RD8] – Application, service, topology information.
- ETSI GS MEC 037 [RD11] – MEC application package format.
- ETSI GS MEC 021 [RD10] – Application mobility (relocation).
<!-- Page 39 -->

Figure 15 - MEO main building blocks, from ETSI GS MEC 003 As depicted in Figure 15, the MEO holds various interfaces with several other major components of the MEC ecosystem. However, in this analysis the focus will be on Mm1 and Mm3 reference points, as these two are mandatory for MEO, and best represent the situation of the 5G-EMERGE ecosystem.

#### 3.1.1 Definition of a MEC Orchestrator

According to the definition of the ETSI MEC Orchestrator (MEO) defined in the ETSI MEC document Multi-access Edge Computing (MEC); Framework and Reference Architecture [RD2]), MEO is the core functionality in MEC system level management. The MEO is responsible for the following functions: 1. Maintaining an overall view of the MEC system based on deployed MEC hosts, available resources, available MEC services, and topology; 2. On-boarding of application packages, including checking the integrity and authenticity of the packages, validating application rules and requirements and if necessary, adjusting them to comply with operator policies, keeping a record of on-boarded packages, and preparing the Virtualisation infrastructure manager(s) to handle the applications; 3. Selecting appropriate MEC host(s) for application instantiation based on

<!-- Page 40 -->

constraints, such as latency, available resources, and available services; 4. Triggering application instantiation and termination; 5. Triggering application relocation as needed when supported; 6. Coordinating with the OSS the application instantiation lifecycle management operations.

#### 3.1.2 Mapping to ETSI MEC Specifications and Reference

Points The following section maps each of the MEC orchestrator responsibilities listed above to the relevant ETSI MEC specifications and reference points that a Nuvla- based MEO implementation would need to support.

- Maintaining an overall view of the MEC system (deployed MEC hosts,
resources, services, topology)

- Relevant specs: ETSI GS MEC 003 (system-level roles and reference
points), ETSI GS MEC 010-2 (MEO–MEPM orchestration flows), ETSI GS MEC 011 (service and application information used for catalog/topology).

- Reference points:
- Mm3 (MEO–MEPM) – MEO as client, MEPM as server (MEO
queries capabilities/resources/hosts, receives status).

- On-boarding of application packages (integrity/authenticity checks,
rule/requirement validation, operator policies, catalogue of packages, preparing the VIM)

- Relevant specs: ETSI GS MEC 003 (MEO responsibilities for on-
boarding), ETSI GS MEC 010-2 (information used during instantiation), ETSI GS MEC 011 (application descriptors, traffic/DNS rules, service requirements), ETSI MEC 037 (MEC app package format).

- Reference points:
- Mm1 (MEO–OSS) – OSS as client, MEO as server; OSS can
submit application on-boarding requests and associated policies/intents, which the MEO then realizes via its catalog and southbound interfaces.

- Mm3 (MEO–MEPM) – MEO as client, MEPM as server (MEO
<!-- Page 41 -->

provides package identifiers/requirements and requests preparation of hosts/VIM for supported apps).

- Selecting appropriate MEC host(s) for application instantiation (latency,
resources, services)

- Relevant specs: ETSI GS MEC 003 (placement logic owned by MEO),
ETSI GS MEC 010-2 (instantiate/terminate flows where host is an input), ETSI GS MEC 011 (application rules/requirements that drive placement).

- Reference points:
- Mm1 (MEO–OSS) – OSS as client, MEO as server; OSS may
provide placement-related constraints or intents (e.g. policy, region) while MEO retains detailed placement logic.

- Mm3 (MEO–MEPM) – MEO as client, MEPM as server (MEO
queries resource/capability state and sends placement decisions, indicating target host/MEPM).

- Triggering application instantiation and termination
- Relevant specs: ETSI GS MEC 003 (high-level responsibility for app
lifecycle), ETSI GS MEC 010-2 (Application LCM API: instantiate, terminate, operate, query operations and states).

- Reference points:
- Mm1 (MEO–OSS) – OSS as client, MEO as server, used to trigger
application instantiation/termination/operate requests at MEC system level and to coordinate application instantiation lifecycle procedures.

- Mm3 (MEO–MEPM) – MEO as client, MEPM as server (MEO
invokes instantiate/terminate/operate/query on MEPM for specific app instances).

- Triggering application relocation as needed when supported
- Relevant specs: ETSI GS MEC 003 (relocation as part of MEO
responsibilities), ETSI GS MEC 010-2 (use of terminate + instantiate / operate operations to realize relocation workflows), ETSI GS MEC 021 (Application Mobility Service API, when used to trigger/coordinate mobility and state transfer).

- Reference points:
<!-- Page 42 -->

- Mm1 (MEO–OSS) – OSS as client, MEO as server, used to
request/trigger relocation at service level and to consume relocation progress/events reported by the MEO.

- Mm3 (MEO–MEPM) – MEO as client, one or more MEPMs
as servers (MEO co-ordinates relocation by orchestrating terminate on the source host and instantiate on the target host, and by tracking resulting states). Note on other MEC 003 reference points: Mm4 (OSS–VIM), Mm9 (MEO–user app LCM proxy) and Mfm (MEO–MEC federator) also appear in the reference architecture and are relevant for infrastructure management, user-app LCM proxying and MEC federation respectively. They are considered out of scope for a Minimum Viable MEO and therefore not mapped in detail in this document.

#### 3.1.3 Gap Analysis vs. MEC Orchestrator Responsibilities

Nuvla already provides a rich set of capabilities (module catalogue, edge inventory and monitoring, deployment and job system, eventing, ACLs) that can be adapted to fulfil MEC orchestrator responsibilities. What is missing is not basic functionality, but rather MEC-compliant control surfaces, data models and workflows that sit on top of these existing building blocks. The MEC orchestrator responsibilities above therefore translate into the following adaptation and implementation items, ordered by the sequence in which they should be addressed: 1. On-boarding of application packages (Adaptation: MEC-compliant onboarding workflow) Nuvla already has a mature notion of modules/packages and a catalogue service. To support MEC-compliant onboarding, this existing machinery needs to be extended with ETSI MEC 037 descriptors and coordinated over Mm1/Mm3. Concrete work items include: a clear OSS-driven onboarding API over Mm1, storage and versioning of MEC app packages and descriptors, and integration of onboarding with southbound preparation of hosts (Mm3 plus any NFV-MANO/VIM integration). 2. Triggering application instantiation and termination (Adaptation: MEC 010-2 lifecycle layer) Nuvla already supports creation, update and deletion of deployments on edge devices. To align with MEC 010-2, this lifecycle control needs to be

<!-- Page 43 -->

surfaced through a MEC-compatible API and data model (AppInstanceInfo, InstantiateAppRequest, AppLcmOpOcc, state model, ProblemDetails) and mapped cleanly to existing deployment resources. Implementing this lifecycle layer is the core orchestration capability required for MEO compliance. 3. Coordinating with the OSS the application instantiation lifecycle management operations (Adaptation: standardised Mm1 northbound interface) Nuvla already exposes APIs and UI workflows for managing deployments, but they are not yet profiled as a standardised Mm1 interface. The next step is to define and stabilise a northbound Mm1 interface so OSS can submit orders/policies and query lifecycle status, alarms and performance information using MEC semantics. 4. Selecting appropriate MEC host(s) for application instantiation (Adaptation: placement engine and MEC host profiling) Nuvla already maintains rich metadata and monitoring information about NuvlaBox/NuvlaEdge devices. To align with MEC expectations, this existing edge inventory should be profiled as MEC hosts by adding MEC-specific attributes (e.g. supported MEC services, zones, policies) and coupling it with a placement engine that takes MEC app requirements and policies as input (from descriptors and Mm1) and matches them with host capabilities and available services (via Mm3 and internal metrics), with deterministic selection, policy adherence and clear handling of placement failures. 5. Triggering application relocation as needed when supported (Adaptation: standardised relocation orchestration) Nuvla already supports a form of migration by cloning and redeploying stateless applications on another device. MEC-compliant relocation requires building on this foundation and exposing relocation as a first-class lifecycle operation (over Mm1), orchestrating coordinated terminate and instantiate sequences via Mm3, and, for stateful apps, integrating with data/state handling to minimise downtime and preserve service continuity. 6. Validating application rules and requirements and adjusting them to comply with operator policies (Adaptation: policy enforcement in the pipeline) 7. A feasibility study for integrating OPA was positive, and Nuvla already processes IaC-like deployment descriptors. The remaining work is to insert a policy validation step into onboarding and instantiation, using an enforced

<!-- Page 44 -->

policy engine that evaluates MEC app rules/requirements (from descriptors/IaC) against operator policies and blocks or normalises deployments before they reach the lifecycle engine.Checking the integrity and authenticity of the packages (Adaptation: end-to-end signature enforcement) Partial Docker Content Trust support already exists. To reach MEC expectations, this needs to be generalised into a complete, cross-platform integrity solution: consistent signing and verification for Docker and Kubernetes artefacts (e.g. DCT/Notary, cosign), policy rules that define which signatures are required, and integration of signature checks into the MEC package onboarding flow.

### 3.2 Assessment of compliance with 3GPP

#### 3.2.1 3GPP Management Architecture and Nuvla Positioning

The 3GPP ecosystem defines two complementary pillars for enabling and operating edge applications: 1. The 3GPP EDGEAPP architecture [RD16], which provides the functional enablers that allow UEs and applications to discover, select, register, and consume edge services in a 5G network environment. 2. The 3GPP Edge Computing Management Architecture, specified in 3GPP TS 28.538 [RD19], which provides the management and orchestration framework required to supervise and operate these edge functions throughout their lifecycle. As introduced in Section 2.2.3, EDGEAPP (TS 23.558) defines the logical roles, interfaces, and procedures needed for 3GPP-compliant edge application deployments. It standardizes the interactions between Edge Application Clients (EAC), Edge Application Servers (EAS), Edge Enabler Clients (EEC) and Edge Enabler Servers (EES). Its goal is to ensure that edge applications can seamlessly discover the optimal execution point, maintain service continuity, and benefit from underlying 3GPP capabilities such as NEF-based exposure, local breakout, and network analytics. However, while EDGEAPP defines what must happen at the edge for applications to operate, it does not define how these components must be orchestrated or managed. This responsibility is entirely delegated to the 3GPP management framework in TS 28.538, which specifies the management services, lifecycle models, performance supervision, and fault handling mechanisms for EAS, EES, and related edge components. 3GPP TS 28.538 therefore acts as the management backbone complementing the EDGEAPP functional architecture.

<!-- Page 45 -->

Most importantly, 3GPP explicitly acknowledges that the Edge Hosting Environment Management layer defined in TS 28.538 may be realized using ETSI MEC. This alignment is intentional and strategic: it avoids duplicating management frameworks and recognizes ETSI MEC, and specifically the MEC orchestrator,as the natural standardized orchestration substrate for edge-cloud deployments. Because 3GPP relies on the ETSI MEC orchestrator as a valid implementation path for edge orchestration, Nuvla naturally positions itself as the MEC Orchestrator (MEO) within the 3GPP TS 28.538 architecture. This means that Nuvla does not need to implement the full 3GPP Management Services (MnS) or adopt the 3GPP NRM to be considered 3GPP-compliant for edge orchestration. Instead, by aligning with ETSI MEC APIs, data models and workflows — particularly ETSI MEC 003 (architecture), ETSI MEC 010-2 (lifecycle management) and MEC 037 (packaging)— Nuvla inherently satisfies the operational expectations placed on an edge orchestrator by 3GPP TS 28.538. In this role, Nuvla delivers the core management functions required for supervising edge workloads:

- Lifecycle management of applications (instantiate, terminate, update).
- Distributed resource inventory and host capability tracking.
- Orchestrated deployment of edge workloads across heterogeneous sites.
- Policy-driven placement and coordination with OSS-level intents.
- Monitoring, eventing, and compliance with MEC’s state and error models.
This approach also aligns seamlessly with the broader 3GPP edge ecosystem, including CAPIF, SEAL, and EDGEAPP, which rely on standardized northbound APIs and service exposure frameworks that can be consumed by or integrated into Nuvla’s orchestration flows. Adopting ETSI MEC as the core standard for orchestration (and relying on MEC compliance to satisfy 3GPP interoperability) offers three clear benefits for 5G-EMERGE: 1. Harmonization with industry-recognized edge orchestration models: ETSI MEC sees broader industry adoption than native 3GPP MnS/NRM interfaces, reducing integration complexity. 2. Interoperability across different edge ecosystems: because ETSI MEC is designed to work with non-3GPP systems as well, Nuvla can support hybrid, satellite-assisted or multi-cloud environments, all core aspects of the 5G-EMERGE architecture.

<!-- Page 46 -->

3. Direct mapping to 3GPP expectations: since 3GPP TS 28.538 explicitly recognizes the MEO role as a valid realization of its management layer, aligning Nuvla with ETSI MEC inherently ensures compatibility with 3GPP edge management requirements. In summary, compliance with ETSI MEC directly enables Nuvla to fulfil the orchestration responsibilities expected by 3GPP for EDGEAPP-based edge deployments, without implementing the full 3GPP management stack. This positioning maximizes interoperability while minimizing architectural duplication, and provides a clear, standards-aligned roadmap for improving Nuvla’s compliance in future releases.

#### 3.2.2 Gap Analysis vs. 3GPP Management Responsibilities

Although Nuvla can effectively act as the MEC Orchestrator within the 3GPP edge management framework, a detailed analysis of the 3GPP specifications, particularly EDGEAPP (TS 23.558) and the 3GPP Edge Computing Management Architecture (TS 28.538), reveals several gaps to be considered to ensure full functional alignment with 3GPP expectations. 1. Lack of native support for 3GPP-specific Management Services (MnS) and Network Resource Model (NRM): The 3GPP management architecture defines a structured set of Management Services (MnS) and a formal Network Resource Model (NRM) for representing EAS/EES resources and their lifecycle. These MnS interfaces are exposed through RESTful APIs following 3GPP OpenAPI conventions, and they are used by the ECSP Management System to interact with the hosting environment. Nuvla does not currently implement these MnS primitives, nor does it expose a 3GPP-compliant NRM. However, because 3GPP TS 28.538 explicitly recognizes ETSI MEC as a valid hosting-environment management solution, NRM/MnS support is not strictly required if Nuvla provides MEC-compliant interfaces. Still, this represents a gap in strict 3GPP compliance, and it may become relevant for future deployments that expect native 3GPP MnS introspection or NRM-based resource modelling. 2. No direct integration with EDGEAPP discovery, selection, and registration workflows EDGEAPP defines the enabler layer for UE- and application-centric

<!-- Page 47 -->

interactions (EAC–EEC–EES–EAS). While the management of EDGEAPP components is delegated to 3GPP TS 28.538, there are still EDGEAPP-specific operational expectations, such as:

- AS/EES registration with discovery frameworks
- Application context relocation support
- Policy alignment with CAPIF-based exposure
- Dependency on 3GPP-native concepts such as PDU session
anchoring or local breakout Nuvla currently does not implement EDGEAPP-specific orchestration logic or data models. Instead, it manages containerized EAS equivalents through general ETSI MEC lifecycle workflows. This covers the core lifecycle needs but lacks the fine-grained EDGEAPP semantics (e.g., EAS discovery attributes, EES configuration flows). 3. Absence of standardized MEC-aligned control surfaces (Mm1/Mm3) required to serve as a de-facto 3GPP edge manager To act as the MEO in a 3GPP-aligned deployment, Nuvla must expose control surfaces consistent with ETSI MEC, from which 3GPP derives its management alignment. This includes:

- Mm1 for OSS-driven onboarding, lifecycle operations, intents, and
policy ingestion

- Mm3 for MEPM coordination, placement decisions, and resource
preparation Today, Nuvla offers strong underlying functionality (deployment engine, inventory, monitoring, eventing) but these capabilities are not yet surfaced using MEC-compliant APIs or data models, which in turn prevents full mapping to the management flows defined in 3GPP TS 28.538. This gap is the most structurally important, because ETSI MEC compliance is the primary mechanism through which Nuvla achieves 3GPP compliance. 4. No stateful relocation or mobility-aware orchestration aligned with EDGEAPP or MEC 021 While Nuvla can redeploy workloads across nodes, it does not yet provide:

- MEC 021-compliant mobility/relocation workflows.
<!-- Page 48 -->

- 3GPP EDGEAPP session continuity support.
- Data-plane migration triggers based on UE mobility or network
signals. This creates a gap for use cases where service continuity, EAS relocation, or mobility-driven orchestration are required by the 3GPP edge architecture. 5. Partial support for 3GPP security expectations (CAPIF, authorization, API exposure) 3GPP edge architectures depends on:

- CAPIF for secure network/API exposure
- Standard authentication/authorization mechanisms
- Auditing and charging hooks for API invocations
Nuvla does not currently integrate with CAPIF or expose APIs using CAPIF-compliant onboarding, access control, or auditing flows. This is not required for basic orchestration roles but becomes a gap for deployments that integrate tightly with the 3GPP core or rely on NEF/CAPIF exposure for application enablement. 6. No built-in support for 3GPP-defined performance, fault and assurance models 3GPP TS 28.538 defines the following:

- Specific telemetry models
- Alarm/event semantics
- Managed Object states
- Performance indicators for EAS/EES supervision
Nuvla provides monitoring and telemetry through its internal mechanisms but does not map these to 3GPP-defined MOI models or MnS service profiles. This limits interoperability with 3GPP-compliant assurance systems.

### 3.3 Conclusions on Nuvla Compliance

No fundamental blockers were identified for adopting Nuvla as ETSI MEC MEO, and as a consequence as the orchestrator for 3GPP-based edge deployments.

<!-- Page 49 -->

However, several gaps exist between Nuvla’s current interfaces and 3GPP formal management models, especially MnS/NRM, CAPIF integration, mobility support, and EDGEAPP-specific semantics. While Nuvla does not natively implement the 3GPP NRM or MnS interfaces, these are optional in deployments that realize the 3GPP TS 28.538 framework through ETSI MEC. MEC compliance largely covers the orchestration responsibilities expected by 3GPP. Accordingly, Nuvla provides functional compliance with 3GPP requirements for edge workload management without requiring native adoption of 3GPP-specific management models. Therefore, closing the ETSI MEC compliance gaps (Mm1/Mm3, MEC 010-2, MEC 037, MEC 021) also would close most of the practical gaps with 3GPP TS 28.538. As the implementation of the full 3GPP NRM is resource-intensive and potentially redundant for this implementation context, Nuvla will adopt a strategy where 3GPP compliance is achieved via the ETSI MEC interface.

<!-- Page 50 -->

## 4 Feasibility study on standards compliance

The purpose of this feasibility study is to assess how Nuvla can practically evolve toward compliance with ETSI MEC and 3GPP edge computing standards, identifying the technical adaptations, interfaces and capabilities required to align with these reference architectures. The study examines both the current strengths of Nuvla’s orchestration framework and the gaps that must be addressed to ensure interoperability, portability and consistency within telecom-grade, multi-vendor edge environments. By evaluating the technological landscape, standard requirements and integration opportunities across the 5G-EMERGE ecosystem, the feasibility study provides a clear and structured foundation for guiding the evolution of Nuvla toward standards-aligned edge orchestration.

### 4.1 Standard compliance strategy and approach

Becoming compliant with ETSI MEC and aligned with 3GPP edge computing architectures allows Nuvla to operate as a standards-recognized edge orchestrator, enabling seamless integration into multi-vendor, operator-driven and hybrid telecom–cloud environments. Standards compliance ensures that Nuvla uses well-defined, widely accepted APIs, lifecycle models and packaging formats, which greatly simplifying interoperability with mobile operators, satellite networks, broadcasters and industry verticals that increasingly rely on ETSI MEC and 3GPP-based architectures. This positions Nuvla as a technically credible and procurement-ready orchestration component that can be adopted without bespoke integration effort. For Nuvla, the benefits extend beyond compatibility: ETSI MEC/3GPP alignment enhances the portability, predictability and trustworthiness of edge applications, while enabling participation in more advanced orchestration workflows such as mobility-aware placement, network-exposed capabilities, slice-aware deployments, and secure API exposure. It strengthens Nuvla’s role as an orchestrator in converged telecom-cloud ecosystems, expands its relevance in large-scale deployments and increases long-term market sustainability by meeting the interoperability, governance and lifecycle requirements expected in modern 5G/6G edge systems.

<!-- Page 51 -->

### 4.2 Target compliance levels and related features

To evolve Nuvla into a standards-aligned edge orchestration platform, a set of target compliance levels has been defined for the most relevant ETSI MEC and 3GPP specifications. These targets identify which standards are meaningful for interoperability, which features Nuvla should implement, and how feasible their adoption is given Nuvla’s current architecture and the technical landscape described in Sections 2 and 3.

#### 4.2.1 ETSI MEC standards

ETSI MEC is the primary reference for edge application lifecycle management, resource orchestration, packaging, and mobility. Nuvla’s compliance strategy focuses on specifications that yield the highest interoperability and functional alignment. Standard reference Target Feasibility Comment Strong functional compliance as a Nuvla already implements key architectural MEC 003 MEC Orchestrator (MEO), including blocks (deployment engine, host inventory, System-Level Roles topology awareness, resource visibility, High events pipeline) and only requires and Reference and orchestration of distributed edge standardisation of control surfaces and Architecture hosts. metadata structures. Adoption of MEC lifecycle data model Nuvla’s existing LCM functionality MEC 010-2 Application and managed objects (instantiate, terminate, update) maps cleanly Lifecycle (AppInstanceInfo, AppLcmOpOcc, High to ETSI MEC semantics and can be surfaced Management (LCM) state machine, ProblemDetails) and through MEC-aligned APIs. exposure of LCM operations.

<!-- Page 52 -->

Use of MEC-defined data models for Nuvla already handles similar constructs; MEC 011 App & Service app descriptors, service dependencies, High transitioning to ETSI MEC models is Information Models traffic rules, DNS rules, and platform structurally straightforward. capabilities. Nuvla’s modular packaging/catalogue MEC 037 Application Full support for MEC package system can incorporate MEC descriptors with Package Format and structures, metadata, signatures, High incremental adaptation, including signature Descriptor versioning, and onboarding flows. verification and policy checks. Stateless relocation is feasible with current Partial compliance focused on features; stateful continuity requires MEC 021 Application relocation workflows, supporting Medium additional design (externalized state, Mobility Service stateless and trigger-driven migration checkpoint mechanisms, or partner-defined of applications across edge nodes. patterns). Compliance with the ETSI MEC ecosystem is highly feasible, as ETSI MEC aligns with Nuvla’s existing edge-orchestration model. Most required features involve adaptation to standard APIs and data models, not re-architecting. Considering relevance, time and resource constraints, the implementation focus will be put on items with a high feasibility, with the aim of realizing a Minimum Viable ETSI MEC MEO. Detailed levels of compliance targeted for implementation for both Mm1 and Mm3 interfaces are described in the tables in the Annex A, which refer to ETSI MEC 010-2 specification for APIs and endpoints [RD7], with an additional column stating the implementation level in Nuvla.

<!-- Page 53 -->

#### 4.2.2 3GPP standards

3GPP compliance is not pursued through full native implementation of MnS or NRM models but through functional alignment via ETSI MEC, which 3GPP explicitly recognises as a valid realization of the Edge Hosting Environment manager. Standard Target for Nuvla Feasibility Comment reference Support for core EDGEAPP capabilities ETSI MEC lifecycle control and relocation 3GPP TS 23.558 through MEC-aligned orchestration, workflows can satisfy most operational EDGEAPP including deployment of EAS Medium - high requirements without implementing all Architecture equivalents, discovery metadata, and EDGEAPP roles natively. mobility-triggered orchestration. Nuvla can satisfy most management 3GPP TS 28.538 Alignment with lifecycle, performance, requirements through its ETSI MEC Edge Computing and fault management expectations, Medium - high alignment and a telemetry adapter; full Management delivered through MEC-compliant MnS/NRM implementation remains Architecture interfaces and telemetry normalisation. optional. CAPIF / GSMA Ability for external consumers to use Requires integration with an exposure Operator Platform CAPIF/OPG-aligned interfaces to access Medium gateway, but does not require substantial Alignment Nuvla orchestration operations. changes to Nuvla’s internal architecture A functionally compliant and operator-ready level of 3GPP alignment is feasible through MEC compliance, telemetry normalization, and integration with CAPIF/OPG exposure layers. Full MnS/NRM support is optional and not necessary for interoperability. Considering relevance, time and resource constraints, the implementation focus will be put on items with a high feasibility with the aim of realizing a Minimum Viable standard compliant solution.

<!-- Page 54 -->

#### 4.2.3 Implementation Strategy

As a result of the feasibility study reported above, the strategy to enhance the Nuvla orchestration solution for standards compliance will follow a two steps implementation approach. Step 1: Become a Recognisable ETSI MEC Orchestrator The first increment focuses on transforming Nuvla’s existing orchestration capabilities into a standards-aligned implementation that clearly fits the role of a MEC Orchestrator within the ETSI MEC architecture. This requires introducing MEC-compliant semantics into Nuvla’s API surface, data models, and lifecycle coordination, ensuring interoperability with OSS systems and other MEC components. A core part of this transformation is the adoption of MEC-compliant APIs and data models. Nuvla’s internal lifecycle engine already supports deployment, update, and removal of edge workloads, but ETSI MEC compliance requires these operations to be expressed through ETSI MEC 010-2 lifecycle managed objects, such as AppInstanceInfo, AppLcmOpOcc, and the MEC-defined state model. Implementing these objects ensures that external systems can track application state transitions using standard terminology and predictable workflows. Likewise, error handling must adopt ETSI MEC-compatible error management (through adoption of ProblemDetails managed objects), enabling operators to uniformly interpret failure cases across MEC platforms. This first step also introduces a standardised Mm1 northbound interface, which becomes the primary entry point for OSS-driven orchestration. Through Mm1, external systems will be able to request onboarding, instantiation, and termination of applications, as well as retrieve lifecycle status and operational results. The key requirement is that Mm1 operations are cleanly mapped to existing Nuvla workflows, so that Nuvla’s internal logic remains stable while still providing a standards-aligned external interface. This creates a clean separation between internal implementation and external standards compliance, reducing architectural friction while maximising interoperability. Step 2: Align Package Management with ETSI MEC Expectations Once Nuvla’s lifecycle and interface behaviour is aligned with the MEC orchestrator role, the second phase focuses on ensuring that application packages handled by Nuvla conform to ETSI MEC expectations, thereby enabling interoperability across MEC-based ecosystems. This begins with extending Nuvla’s module catalogue to fully support ETSI MEC 037

<!-- Page 55 -->

descriptors, which define the standardised format, metadata, and structure of MEC application packages. Supporting ETSI MEC 037 ensures that applications packaged according to MEC specifications can be onboarded and managed by Nuvla without requiring custom transformations, significantly improving portability across vendors and operators. It also ensures that Nuvla-managed apps include the required attributes for lifecycle management, resource requirements, service dependencies and rules. Package alignment also requires introducing OSS-driven onboarding over Mm1, enabling operators to initiate application onboarding directly through standard interfaces. This supports automated workflows and ensures that the entire onboarding process—from receiving a MEC package, validating it, and preparing it for deployment — adheres to ETSI MEC procedures. This second step further integrates the onboarding workflow with southbound host preparation via Mm3, ensuring that external MEC platform managers (MEPM) and virtual infrastructure managers (VIMs) receive the information they need to allocate resources, configure services and prepare hosts for application instantiation — again using standardised reference points. Finally, ETSI MEC application packages require clear versioning and traceability, enabling operators to track which version of a MEC app is deployed, updated or retired. Enhancing Nuvla’s catalogue with versioned entries and provenance metadata provides the transparency and operational control expected in ETSI MEC-compliant deployments.

## 5 Enhancements to Nuvla edge orchestration for

standards compliance The implementation of enhancements to the Nuvla edge orchestration solution to improve its compliance with standards are planned to be carried out starting from Q1-2026, and be included as part of MS3 results and achievements. On the other hand, the focus for MS2 has been on finalizing the compliance study of the Nuvla edge orchestration solution against ETSI MEC and 3GPP, as well as on deriving a reference baseline of relevant standard features together with a feasibility study for implementation.

<!-- Page 56 -->

As a consequence, the plan for integration of the Nuvla orchestrator enhanced with improved standard compliance is currently set for MS3 period, with a candidate start in Q2-2026. As the Nuvla orchestrator is already integrated in testbeds of Strand 3, 4 and 5 from 5G-EMERGE Phase-1, the plan at the time of writing is to upgrade to the new version in all of the testbeds. Figure 16 – High-level plan of activities for the standard compliance of the orchestration software The time-plan of the implementation activities for the enhancement of Nuvla edge orchestration solution towards standards compliance is shown in Figure 15 (with reference to the strategy indicated in section 4.2.3). Figure 17 – Timeplan for enhancement of Nuvla edge orchestration solution towards standards compliance

### 5.1 Design of the Nuvla edge orchestration enhancements

<to be provided in next releases>

### 5.2 Report on implementation

<to be provided in next releases>

<!-- Page 57 -->

## 6 Conclusions

This document has provided the second version of the technical note on the compliance of the orchestration software with ETSI-MEC and 3GPP standards, summarizing the activities carried out in WP2.3.2 related to technology watch and standards review.. The technology watch has been extended considering security aspects in ETSI MEC and 3GPP. Moreover, the compliance study of Nuvla edge orchestration solution against ETSI MEC and 3GPP standards has been documented, with the identification of main gaps against relevant specifications.. The document has also reported on the feasibility study on standards compliance for the Nuvla edge orchestration solution, which has been finalized with the identification of target standards features to be implemented, and a suggested implementation strategy and plan. All of these are serving as an input for the implementation of related enhancements to the Nuvla edge orchestration solution to reach the target compliance level.

<!-- Page 58 -->

## Annex A – ETSI MEC 010-2 APIs – Target levels of compliance

Table 6 - Overview of resources and methods of MEO's application package management on Mm1 - Implementation in Nuvla Resource Resource URI Description Implementation name Application /app_packages Create and query on-boarded Yes packages /onboarded_app_packages application package. Individual /app_packages/{appPkgId} Read, modify and delete an individual application Yes /onboarded_app_packages/{appDId} on-boarded application package. package /app_packages/{appPkgId}/appd Application Read application descriptor of an /onboarded_app_packages/{appDId}/ Yes descriptor onboarded application package. appd /app_packages/{appPkgId}/package_ Application content Fetch, and upload application package Yes /onboarded_app_packages/{appDId}/ packages. content package_content

<!-- Page 59 -->

Create and retrieve subscriptions to notifications related to on-boarding Subscriptions /subscriptions Yes and/or changes of application packages. Individual Read and delete resources of an /subscriptions/{subscriptionId} Yes subscription individual subscription. Notification Notify application package on-boarding (client provided) Yes endpoint or change. Table 7 - Overview of resources and methods of MEO's/MEAO's application life cycle management on Mm1 - Implementation level in Nuvla Resource Resource URI Description Implementation name Application Create and query application instance /app_instances Yes instances resource. Individual Read and delete application instance application /app_instances/{appInstanceId} Yes resource. instance

<!-- Page 60 -->

Instantiate /app_instances/{appInstanceId}/ application Instantiate the application instance. Yes instantiate instance task Terminate /app_instances/{appInstanceId}/ application Terminate the application instance. Yes terminate instance task Operate /app_instances/{appInstanceId}/ application Start or stop the application instance. Yes operate instance task Application LCM Query multiple application lifecycle /app_lcm_op_occs Yes operation operation occurrences. occurrences Individual Read the operation state of the application individual application lifecycle LCM /app_lcm_op_occs/{appLcmOpOccId} Yes operation operation occurrence. occurrence Application /app_lcm_op_occs/{appLcmOpOccId}/ Cancel an ongoing individual No

<!-- Page 61 -->

LCM cancel application LCM operation. operation cancel Application /app_lcm_op_occs/{appLcmOpOccId}/ Finally fail an ongoing individual LCM No fail application LCM operation. operation fail Application LCM /app_lcm_op_occs/{appLcmOpOccId}/ Retry an ongoing individual application No operation retry LCM operation. retry Create and query subscriptions to Subscriptions /subscriptions notifications related to Yes application instances' lifecycle change. Individual Read and delete individual subscription /subscriptions/{subscriptionId} Yes subscription resources. Notification Notify about application instance's (client provided) Yes endpoint lifecycle change.

<!-- Page 62 -->

Table 8 - Overview of resources and methods of MEO's application package management on Mm3 - Implementation level in Nuvla Resource Resource URI Description Implementation name Query information about Application /app_packages multiple on-boarded application Yes packages /onboarded_app_packages packages. Individual Read information about /app_packages/{appPkgId} application individual on-boarded Yes /onboarded_app_packages/{appDId} package application package. Application /app_packages/{appPkgId}/ Fetch an on-boarded application package package_content/onboarded_app_packages/ Yes package. content {appDId}/ package_content Read the application descriptor Application /app_packages/{appPkgId}/appd of the on-boarded application Yes descriptor /onboarded_app_packages/{appDId}/ appd package. Create and retrieve subscriptions Subscriptions /subscriptions Yes to notifications related to on-

<!-- Page 63 -->

boarding and/or changes of application packages. Individual Read and delete an individual /subscriptions/{subscriptionId} Yes subscription subscription. Notification Notify application package on- (client provided) Yes endpoint boarding or change. Table 9 - Overview of resources and methods of MEPM's application life cycle management on Mm3 - Implementation level in Nuvla Resource Resource URI Description Implementation name Application Create and retrieve an application /app_instances Yes instances instance resource Individual application /app_instances/{appInstanceId} Read and delete application instance Yes instance Instantiate /app_instances/{appInstanceId}/ application Instantiate an application instance Yes instantiate instance task

<!-- Page 64 -->

Terminate /app_instances/{appInstanceId}/ application Terminate an application instance Yes terminate instance task Operate /app_instances/{appInstanceId}/ application Start or stop an application instance Yes operate instance task Application LCM Query multiple application lifecycle /app_lcm_op_occs Yes operation operation occurrences occurrences Individual application Read an individual application LCM /app_lcm_op_occs/{appLcmOpOccId} lifecycle Yes operation management operation occurrence occurrence Application LCM /app_lcm_op_occs/{appLcmOpOccId}/ Cancel an ongoing individual No operation cancel application LCM operation cancel

<!-- Page 65 -->

Application /app_lcm_op_occs/{appLcmOpOccId}/ Finally fail an ongoing individual LCM No fail application LCM operation operation fail Application /app_lcm_op_occs/{appLcmOpOccId}/ Retry an ongoing individual LCM No retry application LCM operation operation retry Create and retrieve to subscriptions to Subscriptions /subscriptions notifications related to application Yes instance's lifecycle change Individual /subscriptions/{subscriptionId} Query an individual subscription Yes subscription Terminate an individual subscription Yes Notification Notify about application instance's (client provided) Yes endpoint lifecycle change Table 10 - Overview of resources and methods of MEO's application life cycle management on Mm3 - implementation level in Nuvla Resource Resource URI Description Implementation name

<!-- Page 66 -->

Request a grant for a particular application LCM Grants /grants No operation Individual Read the status of grant for the application LCM /grants/{grantId} No grant operation Table 11 - Overview of resources and methods of MEPM-V's application life cycle management on Mm3 – implementation level in Nuvla Resource Resource URI Description Implementation name Application Create and retrieve an application /app_instances Yes instances instance resource Individual application /app_instances/{appInstanceId} Read and delete application instance Yes instance Providing configuration information Configure in /app_instances/{appInstanceId}/ application AppD to the MEPM-V, intended to No configure_platform_for_app instance task configure the MEP to run an application instance which is

<!-- Page 67 -->

instantiated from the AppD Terminate /app_instances/{appInstanceId}/ Terminate an application instance at application Yes terminate application level instance task Operate Start or stop an application instance /app_instances/{appInstanceId}/ application at Yes operate instance task application level Application LCM Query multiple application lifecycle /app_lcm_op_occs Yes operation operation occurrences occurrences Individual application Read an individual application LCM /app_lcm_op_occs/{appLcmOpOccId} lifecycle Yes operation management operation occurrence occurrence Application /app_lcm_op_occs/{appLcmOpOccId}/ Cancel an ongoing individual LCM operation No cancel application LCM operation cancel

<!-- Page 68 -->

Application /app_lcm_op_occs/{appLcmOpOccId}/ Finally fail an ongoing individual LCM No fail application LCM operation operation fail Application /app_lcm_op_occs/{appLcmOpOccId}/ Retry an ongoing individual LCM No retry application LCM operation operation retry Create and retrieve subscriptions to Subscriptions /subscriptions notifications related to application Yes instance's lifecycle change Individual Query and delete an individual /subscriptions/{subscriptionId} Yes subscription subscription Notification Notify about application instance's (client provided) Yes endpoint lifecycle change
