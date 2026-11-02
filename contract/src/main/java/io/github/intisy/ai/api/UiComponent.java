package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsEnum;
import io.github.intisy.ai.tsemit.TsLiteral;

/** What a node is; a host draws each in its own style. */
@TsEnum
public enum UiComponent {
    /** Children one after another. */
    @TsLiteral("stack")
    STACK,

    /** Children in a twelve-column grid. */
    @TsLiteral("grid")
    GRID,

    /** Two resizable panes. */
    @TsLiteral("split")
    SPLIT,

    /** Children as tabs, one shown at a time. */
    @TsLiteral("tabs")
    TABS,

    /** A titled group of children. */
    @TsLiteral("section")
    SECTION,

    /** A bordered group of children. */
    @TsLiteral("card")
    CARD,

    /** A rule between neighbours. */
    @TsLiteral("divider")
    DIVIDER,

    /** A run of text. */
    @TsLiteral("text")
    TEXT,

    /** Rows of one value each. */
    @TsLiteral("list")
    LIST,

    /** Rows of several columns. */
    @TsLiteral("table")
    TABLE,

    /** Rows nested under rows. */
    @TsLiteral("tree")
    TREE,

    /** Labelled values of one record. */
    @TsLiteral("detail")
    DETAIL,

    /** A chart, in the form its chart property names. */
    @TsLiteral("chart")
    CHART,

    /** Lines appended over time. */
    @TsLiteral("log")
    LOG,

    /** Source text. */
    @TsLiteral("code")
    CODE,

    /** Two texts compared. */
    @TsLiteral("diff")
    DIFF,

    /** A short status label. */
    @TsLiteral("badge")
    BADGE,

    /** A host icon. */
    @TsLiteral("icon")
    ICON,

    /** An editor for one setting. */
    @TsLiteral("field")
    FIELD,

    /** A search box. */
    @TsLiteral("search")
    SEARCH,

    /** Editors submitted together. */
    @TsLiteral("form")
    FORM,

    /** A control that runs an action. */
    @TsLiteral("button")
    BUTTON,

    /** Children shown over the view. */
    @TsLiteral("dialog")
    DIALOG,

    /** A brief notice. */
    @TsLiteral("toast")
    TOAST,

    /** Progress towards completion. */
    @TsLiteral("progress")
    PROGRESS,

    /** Work of unknown length. */
    @TsLiteral("spinner")
    SPINNER,

    /** What a view shows when there is nothing to show. */
    @TsLiteral("empty")
    EMPTY,

    /** What a view shows while it loads. */
    @TsLiteral("loading")
    LOADING,

    /** What a view shows when loading failed. */
    @TsLiteral("error")
    ERROR,

    /** An item that is detected or available but not installed, shown with an install action. */
    @TsLiteral("unavailable")
    UNAVAILABLE
}
