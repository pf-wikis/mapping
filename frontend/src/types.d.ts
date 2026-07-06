declare module "pure-context-menu" {
    export type ContextMenuItem =
        | ContextMenuDivider
        | ContextMenuEntry;

    /** Divider item ("-") */
    export type ContextMenuDivider = "-";

    export interface ContextMenuEntry {
        /** Label shown in the menu */
        label: string;

        /** Click handler */
        callback?: (event: MouseEvent) => void;

        /** Prevent menu from closing after click */
        preventCloseOnClick?: boolean;

        /** Whether item is disabled */
        disabled?: boolean;

        /** Custom HTML instead of label */
        html?: boolean;

        /** Additional CSS classes */
        classes?: string[];

        /** Optional custom data */
        [key: string]: any;
    }

    export interface PureContextMenuOptions {
        /** Class applied to root container */
        contextMenuClass?: string;

        /** Dropdown container class */
        dropdownClass?: string;

        /** Divider class */
        dividerClass?: string;

        /** Menu item <li> class */
        menuItemClass?: string;

        /** Inner item class */
        itemClass?: string;

        /** Disabled item class */
        disabledClass?: string;

        /** z-index for menu */
        zIndex?: number;

        /** Global default: close on click */
        preventCloseOnClick?: boolean;

        /** Use list-group style */
        useLists?: boolean;

        listClass?: string;

        listItemClass?: string;

        /** Trigger click on touchstart (mobile optimization) */
        fastClick?: boolean;

        /** Close menu if already open on right-click */
        closeIfOpen?: boolean;

        /** Hook to decide whether to show menu */
        show?: (event: MouseEvent) => boolean;

        /** Minimum width (CSS value) */
        minWidth?: string;

        /** Maximum width (CSS value) */
        maxWidth?: string;

        /** Use Popover API if available */
        popover?: boolean;
    }

    export default class PureContextMenu {
        constructor(
            target: HTMLElement,
            items: ContextMenuItem[],
            options?: PureContextMenuOptions
        );

        /** Replaces menu items dynamically */
        setItems(items: ContextMenuItem[]): void;

        /** Manually show menu */
        show(event: MouseEvent | PointerEvent): void;

        /** Manually hide menu */
        hide(): void;

        /** Destroy instance and remove listeners */
        destroy(): void;

        /** Global config shared across instances */
        static updateGlobalOptions(options: PureContextMenuOptions): void;
    }
}