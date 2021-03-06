/*
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

package com.google.devtools.j2objc.translate;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.devtools.j2objc.ast.AnnotationTypeDeclaration;
import com.google.devtools.j2objc.ast.AnonymousClassDeclaration;
import com.google.devtools.j2objc.ast.EnumDeclaration;
import com.google.devtools.j2objc.ast.SimpleName;
import com.google.devtools.j2objc.ast.TreeUtil;
import com.google.devtools.j2objc.ast.TreeVisitor;
import com.google.devtools.j2objc.ast.TypeDeclaration;
import com.google.devtools.j2objc.util.BindingUtil;
import com.google.devtools.j2objc.util.NameTable;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import java.util.List;
import java.util.Set;

/**
 * Detects variable name collision scenarios and renames variables accordingly.
 *
 * @author Keith Stanger
 */
public class VariableRenamer extends TreeVisitor {

  private List<Set<String>> fieldNameStack = Lists.newArrayList();
  private Set<ITypeBinding> renamedTypes = Sets.newHashSet();

  private void collectAndRenameFields(ITypeBinding type, Set<IVariableBinding> fields) {
    if (type == null) {
      return;
    }
    type = type.getTypeDeclaration();
    collectAndRenameFields(type.getSuperclass(), fields);
    if (!renamedTypes.contains(type)) {
      renamedTypes.add(type);
      Set<String> superFieldNames = Sets.newHashSet();
      for (IVariableBinding superField : fields) {
        superFieldNames.add(superField.getName());
      }
      for (IVariableBinding field : type.getDeclaredFields()) {
        String fieldName = field.getName();
        if (!BindingUtil.isStatic(field) && superFieldNames.contains(fieldName)) {
          fieldName += "_" + type.getName();
          NameTable.rename(field, fieldName);
        }
        // Note: this check is no longer needed since we don't generate
        // properties from fields anymore.
        for (IMethodBinding method : type.getDeclaredMethods()) {
          if (method.getName().equals(fieldName)) {
            NameTable.rename(field, fieldName + "_");
            break;
          }
        }
      }
    }
    for (IVariableBinding field : type.getDeclaredFields()) {
      if (!BindingUtil.isStatic(field)) {
        fields.add(field);
      }
    }
  }

  private void pushType(ITypeBinding type) {
    Set<IVariableBinding> fields = Sets.newHashSet();
    collectAndRenameFields(type, fields);
    Set<String> fullFieldNames = Sets.newHashSet();
    for (IVariableBinding field : fields) {
      fullFieldNames.add(NameTable.javaFieldToObjC(NameTable.getName(field)));
    }
    fieldNameStack.add(fullFieldNames);
  }

  private void popType() {
    fieldNameStack.remove(fieldNameStack.size() - 1);
  }

  @Override
  public void endVisit(SimpleName node) {
    IVariableBinding var = TreeUtil.getVariableBinding(node);
    if (var == null) {
      return;
    }
    var = var.getVariableDeclaration();
    if (var.isField()) {
      // Make sure fields for the declaring type are renamed.
      collectAndRenameFields(var.getDeclaringClass(), Sets.<IVariableBinding>newHashSet());
    } else {
      // Local variable or parameter. Rename if it shares a name with a field.
      String varName = var.getName();
      assert fieldNameStack.size() > 0;
      Set<String> fieldNames = fieldNameStack.get(fieldNameStack.size() - 1);
      if (fieldNames.contains(varName)) {
        NameTable.rename(var, varName + "Arg");
      }
    }
  }

  @Override
  public boolean visit(TypeDeclaration node) {
    pushType(node.getTypeBinding());
    return true;
  }

  @Override
  public void endVisit(TypeDeclaration node) {
    popType();
  }

  @Override
  public boolean visit(EnumDeclaration node) {
    pushType(node.getTypeBinding());
    return true;
  }

  @Override
  public void endVisit(EnumDeclaration node) {
    popType();
  }

  @Override
  public boolean visit(AnnotationTypeDeclaration node) {
    pushType(node.getTypeBinding());
    return true;
  }

  @Override
  public void endVisit(AnnotationTypeDeclaration node) {
    popType();
  }

  @Override
  public boolean visit(AnonymousClassDeclaration node) {
    pushType(node.getTypeBinding());
    return true;
  }

  @Override
  public void endVisit(AnonymousClassDeclaration node) {
    popType();
  }
}
