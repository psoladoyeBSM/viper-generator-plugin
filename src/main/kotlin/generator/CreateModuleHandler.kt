package generator

import com.intellij.CommonBundle
import com.intellij.history.LocalHistory
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.CreateElementActionBase
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.io.File
import java.util.*


fun String.underScoreToUpperCamelCase(): String {
    return this.split("_").joinToString("", transform = String::capitalize)
}

class CreateModuleHandler(
        @Nullable private val mProject: Project,
        @NotNull private val mDirectory: PsiDirectory,
        private val mIsDirectory: Boolean,
        @NotNull private val mDelimiters: String
) : InputValidatorEx {
    private var mErrorText: String? = ""
    var mCreatedModule: PsiDirectory? = null


    override fun checkInput(inputString: String?): Boolean {
        System.out.println("String check input: %s".format(inputString))
        val tokenizer = StringTokenizer(inputString, mDelimiters)
        var vFile: VirtualFile? = mDirectory.virtualFile
        var firstToken = true
        while (tokenizer.hasMoreTokens()) {
            val token = tokenizer.nextToken()
            if (!tokenizer.hasMoreTokens() && (token == "." || token == "")) {
                mErrorText = "Can't create a directory with name '$token'"
                return false
            }
            if (vFile != null) {
                if (firstToken && "~" == token) {
                    val userHomeDir = VfsUtil.getUserHomeDir()
                    if (userHomeDir == null) {
                        mErrorText = "User home directory not found"
                        return false
                    }
                    vFile = userHomeDir
                } else if ("" == token) {
                    vFile = vFile.parent
                    if (vFile == null) {
                        mErrorText = "Not a valid directory"
                        return false
                    }
                } else if ("." != token) {
                    val child = vFile.findChild(token)
                    if (child != null) {
                        if (!child.isDirectory) {
                            mErrorText = "A file with name '$token' already exists"
                            return false
                        } else if (!tokenizer.hasMoreTokens()) {
                            mErrorText = "A directory with name '$token' already exists"
                            return false
                        }
                    }
                    vFile = child
                }
            }
            if (FileTypeManager.getInstance().isFileIgnored(token)) {
                mErrorText = "Trying to create a " + (if (mIsDirectory) "directory" else "package") +
                        " with an ignored name, the result will not be visible"
                return true
            }
            if (!mIsDirectory && token.isNotEmpty() && !PsiDirectoryFactory.getInstance(mProject).isValidPackageName(token)) {
                mErrorText = "Not a valid package name, it will not be possible to create a Java class inside"
                return true
            }
            firstToken = false
        }
        mErrorText = null
        return true
    }

    override fun getErrorText(inputString: String?): String? {
        return mErrorText
    }

    override fun canClose(moduleName: String?): Boolean {
        if (moduleName!!.isEmpty()) {
            showErrorDialog(IdeBundle.message("error.name.should.be.specified"))
            return false
        }

        doCreateElement(moduleName)

        return mCreatedModule != null
    }

    private fun doCreateElement(directoryName: String?) {
        val command = Runnable {
            val run = Runnable {
                val dirPath = mDirectory.virtualFile.presentableUrl
                val actionName = IdeBundle.message(
                        "progress.creating.directory",
                        dirPath,
                        File.separator,
                        directoryName
                )

                val action = LocalHistory.getInstance().startAction(actionName)

                try {
                    mDirectory.checkCreateSubdirectory(directoryName!!)
                    mCreatedModule = mDirectory.createSubdirectory(directoryName.toLowerCase())

                    createFiles(mCreatedModule!!, mCreatedModule!!.name)
                } catch (ex: IncorrectOperationException) {
                    ApplicationManager.getApplication().invokeLater {
                        showErrorDialog(CreateElementActionBase.filterMessage(ex.message))
                    }
                } finally {
                    action.finish()
                }
            }

            ApplicationManager.getApplication().runWriteAction(run)
        }

        CommandProcessor.getInstance().executeCommand(
                mProject,
                command,
                "Create viper module",
                null
        )
    }

    private fun showErrorDialog(message: String) {
        val title = CommonBundle.getErrorTitle()
        val icon = Messages.getErrorIcon()
        Messages.showMessageDialog(mProject, message, title, icon)
    }

    private fun createFiles(directory: PsiDirectory, moduleName: String) {
        val activityTemplate = FileTemplateManager.getInstance(mProject).getInternalTemplate("activity.kt")
        val activityRouterTemplate = FileTemplateManager.getInstance(mProject).getInternalTemplate("activity_router.kt")
        val contractTemplate = FileTemplateManager.getInstance(mProject).getInternalTemplate("contract.kt")
        val interactorTemplate = FileTemplateManager.getInstance(mProject).getInternalTemplate("interactor.kt")
        val moduleTemplate = FileTemplateManager.getInstance(mProject).getInternalTemplate("module.kt")
        val presenterTemplate = FileTemplateManager.getInstance(mProject).getInternalTemplate("presenter.kt")

        val props = FileTemplateManager.getInstance(mProject).defaultProperties
        props.setProperty("COMPANY", "BSM Technologies")
        props.setProperty("MODULE_NAME", moduleName.capitalize())
        props.setProperty("MODULE_NAME_LOWER", moduleName.toLowerCase())

        FileTemplateUtil.createFromTemplate(
                contractTemplate,
                moduleName.capitalize(),
                props,
                directory
        )

        createAll(
                moduleName,
                directory,
                props,
                activityTemplate,
                activityRouterTemplate,
                interactorTemplate,
                moduleTemplate,
                presenterTemplate
        )
    }

    private fun createAll(moduleName: String, directory: PsiDirectory, props: Properties, vararg templates: FileTemplate) {
        templates.forEach { template ->
            FileTemplateUtil.createFromTemplate(template, moduleName.capitalize() + template.name.underScoreToUpperCamelCase(), props, directory)
        }
    }
}