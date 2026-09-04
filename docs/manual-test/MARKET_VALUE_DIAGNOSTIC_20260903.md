# Market value diagnostic — 2026-09-03

| Surface | Before | Problem | After |
|---|---|---|---|
| Market card / price filter / sorting | `TransferNegotiationUseCase.calculateDynamicPlayerPrice` | Existing canonical-looking path | `Player.calculateMarketValue()` through the same preserved formula |
| Negotiation modal / Buy Now label | `Player.calculateMarketValue()` old piecewise formula | Igor: R$ 110.430.000 vs card R$ 29.700.000 | Same canonical value as card |
| Instant Buy debit | dynamic market price | Could debit a different amount than the modal displayed | Same canonical value displayed and debited |
| Squad player detail | persisted `market_value` or old piecewise fallback | Could show a third value while sale used dynamic price | Canonical value only |
| Sale default | dynamic market price | Could differ from squad detail | Same canonical value |
| Offer slider | 50%..150% of old modal value | Range could exclude/contradict the previous screen | 50%..150% of the canonical value; current displayed value is always inside |
| Market position filter | async result stored independently of selected chip | New chip could temporarily render rows from old filter | Result keyed to the exact active criteria; stale rows hidden immediately |
| Editor Técnico | no market-value editor exists in current source | It must not become a separate price authority | No separate price authority introduced |

The preserved canonical formula is the formula already used by the Market list before this fix:
`(force * 150000 + potential * 100000) * ageFactor`, with age factors 1.5 / 1.2 / 0.9 / 0.6.
No sporting data or factual player attributes are changed.
