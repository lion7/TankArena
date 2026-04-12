# Risk Register

## Kubriko Integration Risk
- Risk: runtime embedding or split-screen support may need adaptation.
- Mitigation: keep Kubriko-specific work behind `:game-render-kubriko`.

## Gameplay Parity Risk
- Risk: first rewrite slices drift from legacy feel.
- Mitigation: preserve 50 Hz tick and build replay/checksum tests early.

## Legacy Import Risk
- Risk: raw object blob semantics are broader than initial converter support.
- Mitigation: import layers and mission text first; add typed object conversion incrementally.

