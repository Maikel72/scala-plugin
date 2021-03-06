package org.jetbrains.plugins.scala
package console

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaConsoleRunConfigurationEditor(project: Project, configuration: ScalaConsoleRunConfiguration)
        extends SettingsEditor[ScalaConsoleRunConfiguration] {
  val form = new ScalaConsoleRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaConsoleRunConfiguration): Unit = form(s)

  def disposeEditor: Unit = {}

  def applyEditorTo(s: ScalaConsoleRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}