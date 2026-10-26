<!-- FAILURE_MODES.md: before writing each API, every way it could lose money, create
money, pay twice, block a thread or hang. One line each, committed before the
code that addresses it. At the end, mark each line with how you prevented it, or
"not handled -->


1. Transaction happens on the client(thrid party integration) and we receive no reply
2. Race conditions might be caused  by repeated client side retries
3. Silent webhoook/callback failures 
4. Float point differences
5. Reconsiliation/batch job lags or failures