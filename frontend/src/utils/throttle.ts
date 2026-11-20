/* Leading-edge throttle: calls immediately, then at most once per delay.
   The last call is still executed (delayed) so trailing updates are not lost. */
export default function throttle<Args extends unknown[]>(f: (...args: Args) => void, delay: number) {
    let lastCall = Number.NEGATIVE_INFINITY;
    let handle: ReturnType<typeof setTimeout> | undefined;
    return (...args: Args) => {
        const remaining = lastCall + delay - Date.now();
        clearTimeout(handle);
        if (remaining <= 0) {
            lastCall = Date.now();
            f(...args);
        }
        else {
            handle = setTimeout(() => {
                lastCall = Date.now();
                f(...args);
            }, remaining);
        }
    };
}
