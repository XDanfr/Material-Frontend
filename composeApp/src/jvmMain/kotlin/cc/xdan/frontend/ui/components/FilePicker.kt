package cc.xdan.frontend.ui.components

import cc.xdan.frontend.data.HostOs
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/** Native-feeling pickers (FileDialog on macOS, JFileChooser for folders elsewhere). */
object FilePicker {
    fun pickFile(title: String): File? = onEdt {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
        d.isVisible = true
        d.file?.let { File(d.directory, it) }
    }

    fun pickFolder(title: String): File? = onEdt {
        if (HostOs.current == HostOs.MAC) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
                d.isVisible = true
                d.file?.let { File(d.directory, it) }
            } finally { System.setProperty("apple.awt.fileDialogForDirectories", "false") }
        } else {
            val c = JFileChooser().apply { dialogTitle = title; fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) c.selectedFile else null
        }
    }

    private fun <T> onEdt(block: () -> T): T =
        if (SwingUtilities.isEventDispatchThread()) block() else { var r: T? = null; SwingUtilities.invokeAndWait { r = block() }; @Suppress("UNCHECKED_CAST") (r as T) }
}
