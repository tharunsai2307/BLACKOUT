# BLACKOUT — SMS Gateway Repository Evaluation & Suitability Assessment

## 1. Repository Identification & Metadata

| Attribute | Details |
|---|---|
| **Repository Name** | `sms-api` |
| **Repository URL** | [https://github.com/Moli13337/sms-api](https://github.com/Moli13337/sms-api) |
| **Author / Maintainer** | `Moli1337` (`Moli13337`) |
| **Topics / Tags** | `sms-api`, `free-sms-sender`, `free-api`, `sms-tools` |
| **Evaluation Date** | November 2026 |
| **Platform Target** | BLACKOUT Decentralized Emergency Communications |

---

## 2. Technical Investigation & Architecture Analysis

An exhaustive inspection of the `Moli13337/sms-api` codebase, its dependencies, endpoint definitions, and implementation was conducted to evaluate its suitability for emergency communications.

### 2.1 Origin & Mechanism of Operation
The repository consists of deobfuscated scripts derived from "free SMS" and SMS bomber utility toolboxes. Rather than integrating with an authenticated, licensed telecommunications aggregator or carrier gateway, the repository functions by:
1. Maintaining a hardcoded list of unauthenticated public endpoints belonging to commercial mobile applications (such as food delivery platforms, e-commerce stores, ride-hailing services, and fintech login portals).
2. Firing automated HTTP POST requests with a target phone number to trigger the third party's customer login One-Time Password (OTP) verification SMS.

```
[ Moli13337/sms-api Script ]
             |
             v  (Automated POST to arbitrary third-party OTP login form)
[ Unsuspecting E-Commerce / Food Delivery Service ]
             |
             v  (Triggers commercial login verification SMS)
[ Recipient Mobile Device ]
             --> Receives: "Your login OTP for FoodDeliveryApp is 4821"
                 (ZERO emergency data, ZERO GPS coordinates, ZERO context)
```

---

## 3. Critical Failures Against Emergency Requirements

| Evaluation Requirement | Assessment Outcome | Failure Severity |
|---|---|---|
| **Custom Message Payload Support** | **FAILED**: The caller has **zero control** over the SMS message body. The text delivered is an irrelevant third-party OTP code. Emergency coordinates, SOS descriptions, or evacuation warnings cannot be delivered. | **CRITICAL / SHOWSTOPPER** |
| **Delivery Receipts & Telemetry (DLR)** | **FAILED**: No webhook callbacks, delivery reports, or message delivery receipts exist. The sender receives only an HTTP status from the scraped endpoint, which gives no evidence that an SMS was dispatched or reached a cell tower. | **CRITICAL** |
| **Service Level Agreement (SLA)** | **FAILED**: No uptime or latency guarantees. Scraped endpoints routinely fail, change schemas, or encounter downtime without warning. | **CRITICAL** |
| **Rate Limits & IP Blacklisting** | **FAILED**: Because these endpoints are scraped targets of SMS spam, cloud security providers (Cloudflare, AWS WAF, Akamai) actively block, rate-limit, and CAPTCHA-protect them. Requests will fail rapidly in production. | **HIGH** |
| **Carrier Filtering & Abuse Classification** | **FAILED**: Mass requests to such endpoints are classified by telecommunications operators as SMS bombing / unsolicited spam, resulting in carrier-level blocking. | **HIGH** |

---

## 4. Legal & Telecommunications Regulatory Analysis

Deploying or connecting an unauthorized scraping tool like `Moli13337/sms-api` in an emergency communications platform violates telecommunications law across major jurisdictions:

### 4.1 India: TRAI & TCCCPR 2018 Regulations
In India, the Telecom Regulatory Authority of India (TRAI) enforces the *Telecom Commercial Communications Customer Preference Regulations, 2018* (TCCCPR):
- **Mandatory DLT Registration**: All Application-to-Person (A2P) messages must be routed through an authorized Distributed Ledger Technology (DLT) platform (operated by telecom access providers like Jio, Airtel, Vodafone Idea, or BSNL).
- **Approved Headers / Sender IDs**: SMS must originate from a verified 6-character Alpha Header (e.g. `BLKOUT`, `EMRGNC`). Scraped OTPs originate from arbitrary commercial headers (e.g. `VK-ZOMATO`, `MD-SWIGGY`).
- **Registered Content Templates**: Every emergency SMS template must be pre-approved on the DLT portal with registered variable placeholders (`{#var#}`).
- **Legal Penalties**: Misusing commercial OTP endpoints violates TRAI directives and constitutes unauthorized network interference under the Indian Telegraph Act, 1885, and Information Technology Act, 2000.

### 4.2 United States: TCPA & 10DLC Regulations
- The Federal Communications Commission (FCC) and the Cellular Telecommunications Industry Association (CTIA) strictly mandate 10-Digit Long Code (10DLC) brand and campaign registration for A2P SMS.
- Sending messages via third-party proxy endpoints violates the Telephone Consumer Protection Act (TCPA) and triggers severe civil liability (\$500 to \$1,500 per unauthorized message).

### 4.3 European Union: GDPR & ePrivacy Directive
- Harvesting and transmitting mobile numbers through uncontrolled third-party endpoints violates GDPR Article 6 (Lawfulness of Processing) and Article 32 (Security of Processing).

---

## 5. Formal Recommendation & Architecture Decision

### Verdict: **STRICTLY REJECTED FOR PRODUCTION USE**
The `Moli13337/sms-api` repository is **completely unsuitable, legally non-compliant, and dangerous** for use in an emergency civil defense platform. Relying on it for life-safety communications would lead to catastrophic delivery failures, legal prosecution, and immediate network blacklisting.

### Alternative Production Architecture:
BLACKOUT implements a **Provider-Independent SMS Subsystem** (`ISmsProvider`) that connects exclusively to legitimate, licensed telecommunications aggregators:
1. **India Operations**: Approved TRAI DLT Gateways (Airtel IQ, Jio Enterprise, Tanla / ValueFirst, Gupshup) with registered Entity ID, Header (`BLKOUT`), and pre-approved DLT templates.
2. **Global Operations**: Licensed A2P SMS aggregators (Twilio, AWS SNS, Infobip, Sinch) authenticated via secure server-side API credentials.
3. **Development / Pilot Testing**: Deterministic `MockSmsProvider` that logs transaction payloads without consuming carrier credits or spamming phone networks.

