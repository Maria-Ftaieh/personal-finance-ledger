/**
 * Whether this build is the public demonstration deployment.
 *
 * Used only to shape the interface: controls that cannot work are disabled and explained,
 * rather than looking live and failing on click. The actual enforcement is entirely on the
 * server (`ledger.demo.read-only`), which refuses writes whatever the browser believes —
 * so if the two ever disagree the worst outcome is a button that looks usable and returns
 * a clear message.
 */
export const IS_DEMO = import.meta.env.VITE_DEMO_MODE === "true";

/** Demo deployments accept no changes; see `DemoReadOnlyFilter` on the server. */
export const READ_ONLY = IS_DEMO;
