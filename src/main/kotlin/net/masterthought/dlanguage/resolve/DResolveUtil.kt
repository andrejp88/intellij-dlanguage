package net.masterthought.dlanguage.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import net.masterthought.dlanguage.index.DModuleIndex.getFilesByModuleName
import net.masterthought.dlanguage.psi.DLanguageNewExpression
import net.masterthought.dlanguage.psi.interfaces.Declaration
import net.masterthought.dlanguage.stubs.index.DTopLevelDeclarationIndex
import net.masterthought.dlanguage.utils.*

/**
 * Created by francis on 5/12/17.
 */
object DResolveUtil {
    /**
     * Finds name definition across all Haskell files in the project. All
     * definitions are found when name is null.
     */
    fun findDefinitionNode(project: Project, e: PsiNamedElement): List<PsiNamedElement> {
        fun inSingleImport(e: Identifier): SingleImport? {
            return PsiTreeUtil.getTopmostParentOfType(e, SingleImport::class.java)
        }

        // Guess where the name could be defined by lookup up potential modules.
        if (e !is Identifier) {
            return emptyList()
        }

        if (inSingleImport(e) != null) {
            return getFilesByModuleName(project, inSingleImport(e)!!.name, GlobalSearchScope.allScope(project))
        }

        val nameProcessor = DNameScopeProcessor(e)
        PsiTreeUtil.treeWalkUp(nameProcessor, e, e.containingFile, ResolveState.initial())
        if (nameProcessor.result.size != 0) {
            return nameProcessor.result.toList()
        }

        val importProcessor = DImportScopeProcessor()
        PsiTreeUtil.treeWalkUp(importProcessor, e, e.containingFile, ResolveState.initial())
        val modules: MutableList<String> = mutableListOf()
        (importProcessor.imports).filterNotNull().mapTo(modules) { it.name!! }

        val result = mutableSetOf<PsiNamedElement>()
        // find definition in imported files
        for (module in modules) {
            val files = getFilesByModuleName(project, module, GlobalSearchScope.allScope(project))
            for (f in files) {
                result.addAll(StubIndex.getElements(DTopLevelDeclarationIndex.KEY, e.name!!, e.project, GlobalSearchScope.fileScope(f), Declaration::class.java))
            }
        }
        val finalResult = mutableListOf<PsiNamedElement>()
        for (element in result) {
            if (element is Constructor) {
                if (resolvingConstructor(e)) {
                    finalResult.add(element)
                } else continue
            }
            if (!resolvingConstructor(e)) {
                finalResult.add(element)
            }
        }
        return finalResult
    }

    fun resolvingConstructor(e: PsiElement): Boolean {
        var parent: PsiElement? = e.parent
        while (true) {
            if (parent == null)
                break
            if (parent is DLanguageNewExpression || parent is NewAnonClassExpression)
                return true
            parent = parent.parent
        }
        return false
    }

}
