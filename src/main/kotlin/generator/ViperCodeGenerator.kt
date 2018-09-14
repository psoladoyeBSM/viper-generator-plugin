package generator

import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PlatformIcons

class ViperCodeGenerator : AnAction(
        "Viper Generator",
        "Generates viper architecture files",
        PlatformIcons.FOLDER_ICON
) {

    override fun update(event: AnActionEvent?) {
        val selectedElement = CommonDataKeys.PSI_ELEMENT.getData(event!!.dataContext)

        if (selectedElement !is PsiDirectory) {
            event.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val view = event.getData(LangDataKeys.IDE_VIEW) ?: return
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val directory = DirectoryChooserUtil.getOrChooseDirectory(view) ?: return
        val isDirectory = !PsiDirectoryFactory.getInstance(project).isPackage(directory)

        val validator = CreateModuleHandler(project, directory, isDirectory, if (isDirectory) "\\/" else ".")
        Messages.showInputDialog(
                project,
                "Enter new module name",
                "New VIPER Module",
                Messages.getQuestionIcon(), "", validator
        )

        validator.mCreatedModule?.let {
            view.selectElement(it)
        }
    }
}
