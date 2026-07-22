# Carix Product Design

## Purpose

Carix measures a user's real career value by connecting personal income and
consumption patterns with labor-market skill demand and disclosed salaries.

## Product Model

The product exposes two indices and one categorical decision signal:

1. Purchasing Power Index
2. Market Value Index
3. Decision Signal

The results remain separate so users can understand conflicting signals and
the evidence behind each recommendation. The decision signal is categorical to
avoid false precision.

## MVP Boundary

The first release targets developer, data, and AI roles. Users manually enter
salary history, consumption weights, career attributes, and skills. KOSIS CPI
data supports purchasing-power calculations. Skill demand is measured from job
postings across Work24 and Saramin, and the salary-gap baseline comes from
official wage statistics (posting salaries are shown only as reference). See the
hybrid lineage decision in the feature breakdown §6.1.

The MVP compares staying and changing jobs. It does not include financial
account connections, automatic applications, guaranteed salary predictions, or
professional financial, tax, or labor advice.

## Reliability

Every result must include its source date, sample size, and relevant missing-data
rate. The product must label observed associations as trends rather than causal
effects and must degrade transparently when an external API is unavailable.

## Current Decision

The detailed MVP requirements are approved. The application uses a Vite + React
(React Router, Node SSR) frontend, a Java 21 Spring Boot backend, PostgreSQL, and
a separate Spring Batch worker. Data feasibility for KOSIS, Work24, Saramin, and
the wage-statistics source must be verified before full implementation.
