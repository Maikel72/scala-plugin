/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedImportsPassFactory;
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedSymbolPassFactory;
import org.jetbrains.plugins.scala.debugger.ScalaJVMNameMapper;
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager;
import org.jetbrains.plugins.scala.editor.selectioner.ScalaLiteralSelectioner;
import org.jetbrains.plugins.scala.editor.selectioner.ScalaWordSelectioner;
import org.jetbrains.plugins.scala.lang.editor.ScalaQuoteHandler;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeUtilSupport;

import java.util.Set;

/**
 * @author ilyas
 */
public class ScalaLoader implements ApplicationComponent {

  @NotNull
  public static final String SCALA_EXTENSION = "scala";

  @NotNull
  public static final Set<String> SCALA_EXTENSIONS = new HashSet<String>();

  static {
    SCALA_EXTENSIONS.add(SCALA_EXTENSION);
  }

  public ScalaLoader() {
  }

  public void initComponent() {
    loadScala();
  }

  public static void loadScala() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {

        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompilableFileType(ScalaFileType.SCALA_FILE_TYPE);

        DebuggerManager.getInstance(project).addClassNameMapper(new ScalaJVMNameMapper());

      }
    });


  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "Scala Loader";
  }
}