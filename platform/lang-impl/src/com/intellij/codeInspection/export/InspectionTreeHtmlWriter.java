/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
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
package com.intellij.codeInspection.export;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ex.HTMLComposerImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.ui.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * @author Dmitry Batkovich
 */
public class InspectionTreeHtmlWriter {
  private final InspectionTree myTree;
  private final String myOutputDir;
  private final StringBuffer myBuilder = new StringBuffer();
  private final InspectionProfile myProfile;
  private final RefManager myManager;
  private final ExcludedInspectionTreeNodesManager myExcludedManager;

  public InspectionTreeHtmlWriter(InspectionResultsView view,
                                  String outputDir) {
    myTree = view.getTree();
    myOutputDir = outputDir;
    myProfile = view.getCurrentProfile();
    myManager = view.getGlobalInspectionContext().getRefManager();
    myExcludedManager = view.getExcludedManager();
    serializeTreeToHtml();
  }

  private void traverseInspectionTree(final InspectionTreeNode node,
                                             final Consumer<InspectionTreeNode> preAction,
                                             final Consumer<InspectionTreeNode> postAction) {
    if (node.isExcluded(myExcludedManager)) {
      return;
    }
    preAction.accept(node);
    for (int i = 0; i < node.getChildCount(); i++) {
      traverseInspectionTree((InspectionTreeNode)node.getChildAt(i), preAction, postAction);
    }
    postAction.accept(node);
  }

  private void serializeTreeToHtml() {
    appendHeader();
    appendTree((builder) -> {
      final HTMLComposerImpl[] exporter = new HTMLComposerImpl[] {null};
      final InspectionTreeTailRenderer tailRenderer = new InspectionTreeTailRenderer(myTree.getContext()) {
        @Override
        protected void appendText(String text, SimpleTextAttributes attributes) {
          builder.append(escapeNonBreakingSymbols(text));
        }

        @Override
        protected void appendText(String text) {
          builder.append(escapeNonBreakingSymbols(text));
        }
      };
      traverseInspectionTree(myTree.getRoot(),
                             (n) -> {
                               final int nodeId = System.identityHashCode(n);
                               builder
                                 .append("<li><label for=\"")
                                 .append(nodeId)
                                 .append("\">")
                                 .append(convertNodeToHtml(n))
                                 .append("&nbsp;<span class=\"grayout\">");
                               tailRenderer.appendTailText(n);
                               builder.append("</span></label><input type=\"checkbox\" ");
                               if (n instanceof InspectionRootNode) {
                                 builder.append("checked");
                               }
                               if (n instanceof InspectionNode) {
                                 exporter[0] = myTree.getContext().getPresentation(((InspectionNode)n).getToolWrapper()).getComposer();
                               }
                               builder.append(" onclick=\"navigate(").append(nodeId).append(")\" ");
                               builder.append(" id=\"").append(nodeId).append("\" />");
                               if (n instanceof RefElementAndDescriptorAware) {
                                 RefEntity e = ((RefElementAndDescriptorAware)n).getElement();
                                 if (e != null) {
                                   if (exporter[0] != null) {
                                     builder
                                       .append("<div id=\"d")
                                       .append(nodeId)
                                       .append("\" style=\"display:none\">");
                                     exporter[0].compose(builder, e);
                                     builder.append("</div>");
                                   }
                                 }
                               }
                               builder.append("<ol class=\"tree\">");
                             },
                             (n) -> builder.append("</ol></li>"));
    });

    HTMLExportUtil.writeFile(myOutputDir, "index.html", myBuilder, myTree.getContext().getProject());
    InspectionTreeHtmlExportResources.copyInspectionReportResources(myOutputDir);
  }

  private String convertNodeToHtml(InspectionTreeNode node) {
    if (node instanceof InspectionRootNode) {
      return "<b>'" + escapeNonBreakingSymbols(node) + "' project</b>";
    }
    else if (node instanceof ProblemDescriptionNode) {
      final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)node).getDescriptor();
      String warningLevelName = "";
      Color color = null;
      if (descriptor instanceof ProblemDescriptorBase) {
        final InspectionToolWrapper tool = ((ProblemDescriptionNode)node).getToolWrapper();
        final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getID());
        if (key != null) {

          HighlightSeverity severity = myProfile.getErrorLevel(key, ((ProblemDescriptorBase)descriptor).getStartElement()).getSeverity();
          final HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
          final Icon icon = level.getIcon();
          if (icon instanceof HighlightDisplayLevel.SingleColorIcon) {
            color = ((HighlightDisplayLevel.SingleColorIcon)icon).getColor();
          }
          warningLevelName = level.getName();
        }
      }

      final StringBuilder sb = new StringBuilder();
      sb.append("<span style=\"margin:1px;background:#");
      if (color != null) {
        UIUtil.appendColor(color, sb);
      }
      sb.append("\">");
      sb.append(warningLevelName);
      sb.append("</span>&nbsp;");
      sb.append(escapeNonBreakingSymbols(node));
      return sb.toString();
    }
    else if (node instanceof RefElementNode) {
      final String type = myManager.getType((RefEntity)node.getUserObject());
      return type + "&nbsp;<b>" + node.toString() + "</b>";
    }
    else if (node instanceof InspectionNode) {
      return "<b>" + escapeNonBreakingSymbols(node) + "</b>&nbsp;inspection";
    }
    else if (node instanceof InspectionGroupNode) {
      return "<b>" + escapeNonBreakingSymbols(node) + "</b>&nbsp;group";
    }
    else {
      return escapeNonBreakingSymbols(node);
    }
  }

  private void appendHeader() {
    String title = ApplicationNamesInfo.getInstance().getFullProductName() + " inspection report";
    myBuilder.append("<html><head>" +
                     "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">" +
                     "<meta name=\"author\" content=\"JetBrains\">" +
                     "<script type=\"text/javascript\" src=\"script.js\"></script>" +
                     "<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\"/>" +
                     "<title>")
      .append(title)
      .append("</title></head><body><h3>")
      .append(title)
      .append(":</h3>");
  }

  private void appendTree(Consumer<StringBuffer> treeRenderer) {
    myBuilder.append("<div style=\"width:100%;\"><div style=\"float:left; width:50%;\"><h4>Inspection tree:</h4>");
    treeRenderer.accept(myBuilder);
    myBuilder.append("</div><div style=\"float:left; width:50%;\"><h4>Problem description:</h4>" +
                     "<div id=\"preview\">Select a problem element in tree</div></div><div></body></html>");
  }

  private static String escapeNonBreakingSymbols(@NotNull Object source) {
    return StringUtil.replace(StringUtil.escapeXml(source.toString()), new String[]{" ", "-"}, new String[]{"&nbsp;", "&#8209;"});
  }
}
