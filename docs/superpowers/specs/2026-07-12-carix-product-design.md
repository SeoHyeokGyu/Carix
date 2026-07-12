# Carix Product Design

## Purpose

Carix measures a user's real career value by connecting personal income and
consumption patterns with labor-market skill demand and disclosed salaries.

## Product Model

The product exposes three independent indices:

1. Purchasing Power Index
2. Market Value Index
3. Optionality Index

The indices remain separate so users can understand conflicting signals and
the evidence behind each recommendation.

## MVP Boundary

The first release targets developer, data, and AI roles. Users manually enter
salary history, consumption weights, career attributes, and skills. KOSIS CPI
data supports purchasing-power calculations, while Work24 job postings support
skill-demand and disclosed-salary analysis.

The MVP compares staying and changing jobs. It does not include financial
account connections, automatic applications, guaranteed salary predictions, or
professional financial, tax, or labor advice.

## Reliability

Every result must include its source date, sample size, and relevant missing-data
rate. The product must label observed associations as trends rather than causal
effects and must degrade transparently when an external API is unavailable.

## Current Decision

The repository remains documentation-first until the team approves detailed MVP
requirements and selects the application stack.
