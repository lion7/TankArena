# ADR 0005: Network-Ready Input/Protocol Boundary

- Status: Accepted
- Decision: represent gameplay input as per-tick intent frames and keep replay/protocol DTOs separate from runtime concerns.
- Consequence: networking can be added later without rewriting gameplay ownership.
