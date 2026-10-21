package be.wimvandonck.noter.logic

/** The ways to add a workspace (Settings › Add workspace). */
enum class WorkspaceWay {
    /** Connect to Notion (OAuth): quickest; the person chooses the pages in Notion. */
    SIGN_IN,

    /** A personal access token: everything the person sees in their own workspace. */
    PERSONAL,

    /** A connection's token: for a workspace the person is a guest in; a member shares the pages. */
    CONNECTION,
}

object WorkspaceWays {
    /** The ways offered, quickest first; Connect to Notion only when this build has it ([canSignIn]). */
    fun available(canSignIn: Boolean): List<WorkspaceWay> =
        WorkspaceWay.entries.filter { it != WorkspaceWay.SIGN_IN || canSignIn }

    /** The way chosen when the dialog opens: the first offered. */
    fun default(canSignIn: Boolean): WorkspaceWay = available(canSignIn).first()

    /** A token has to be pasted for [way] (the others need none). */
    fun needsToken(way: WorkspaceWay): Boolean = way != WorkspaceWay.SIGN_IN
}
