package be.wimvandonck.noter.logic

/** The ways to add a workspace (Settings › Workspaces). */
enum class WorkspaceWay {
    /** Connect to Notion (OAuth): quickest; the person chooses the pages in Notion. Its own button. */
    SIGN_IN,

    /** A personal access token: everything the person sees in their own workspace. */
    PERSONAL,

    /** A connection's token: for a workspace the person is a guest in; a member shares the pages. */
    CONNECTION,
}

object WorkspaceWays {
    /** The ways under Advanced, each with a token to paste. */
    val advanced: List<WorkspaceWay> = listOf(WorkspaceWay.PERSONAL, WorkspaceWay.CONNECTION)

    /** The way chosen when Advanced opens. */
    val default: WorkspaceWay = WorkspaceWay.PERSONAL
}
