/*Throttles but also ensures the last call is executed (delayed though) */
export default function throttle(f: Function, delay: number) {
    let lastCall = Number.NEGATIVE_INFINITY;
    let wait;
    let handle:any;
    return (...args: any[]) => {
        wait = lastCall + delay - Date.now();
        clearTimeout(handle);
        handle = setTimeout(() => {
            f(...args);
            lastCall = Date.now();
        }, wait);
    };
}