package app.aaps.core.interfaces.autotune

/**
 * Where the files of an Autotune run are kept: a folder for the run and one for its settings, both in
 * [directory], and a zip of the two. The files are for comparing a run with oref0 autotune on a
 * computer, so they are put where the user can find them.
 *
 * Only the file work is on the platform. What the files are called and what they hold is decided by
 * the Autotune plugin.
 */
interface AutotuneFileStore {

    /** The folder that holds the two folders and the zips. Used in the log only. */
    val directory: String

    /**
     * Makes [folder] if it is missing.
     *
     * @return true when the folder was missing, false when it was there already. The run log says
     *   the folder was made on true, also when making it failed, as the Android code always did.
     */
    fun createFolder(folder: AutotuneFolder): Boolean

    /** Deletes the files in [folder]. Folders inside it are left. */
    fun deleteFiles(folder: AutotuneFolder)

    /**
     * Writes [text] and a line break to the file [name] in [folder], in place of a file there before.
     *
     * @return false when the file could not be written
     */
    fun write(folder: AutotuneFolder, name: String, text: String): Boolean

    /**
     * Writes a zip named [name] into [directory], with the files of both folders, each under its
     * folder name.
     *
     * @return false when the zip could not be written
     */
    fun zip(name: String): Boolean
}

/** The two folders of an Autotune run. [folderName] is the name on the disk. */
enum class AutotuneFolder(val folderName: String) {

    RUN("autotune"),
    SETTINGS("settings")
}
