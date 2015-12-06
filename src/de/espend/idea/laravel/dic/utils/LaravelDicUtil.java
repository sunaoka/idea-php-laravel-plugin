package de.espend.idea.laravel.dic.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.*;
import com.jetbrains.php.lang.psi.elements.*;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class LaravelDicUtil {

    private static final Key<CachedValue<Map<String, Collection<String>>>> DIC_CACHE = new Key<CachedValue<Map<String, Collection<String>>>>("LaravelDicUtilMap");

    synchronized public static Map<String, Collection<String>> getDicMap(@NotNull final Project project) {
        CachedValue<Map<String, Collection<String>>> cache = project.getUserData(DIC_CACHE);

        if(cache == null) {
            cache = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Map<String, Collection<String>>>() {
                @Nullable
                @Override
                public Result<Map<String, Collection<String>>> compute() {
                    return Result.create(getCoreAliasMap(project), PsiModificationTracker.MODIFICATION_COUNT);
                }
            }, false);

            project.putUserData(DIC_CACHE, cache);
        }

        return cache.getValue();
    }

    public static Map<String, Collection<String>> getCoreAliasMap(@NotNull Project project) {
        final Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();

        visitRegisterCoreContainerAliases(project, new DicAliasVisitor() {
            @Override
            public void visit(@NotNull PsiElement value, @NotNull String keyName) {

                Collection<String> values = new ArrayList<String>();

                if(value instanceof StringLiteralExpression) {
                    String contents = ((StringLiteralExpression) value).getContents();
                    if(StringUtils.isNotBlank(contents)) {
                        values.add(contents);
                    }
                } else if(value instanceof ArrayCreationExpression) {
                    values.addAll(PhpElementsUtil.getArrayValuesAsString((ArrayCreationExpression) value));
                }

                map.put(keyName, values);
            }
        });

        return map;
    }

    public static Collection<String> getCoreAliases(@NotNull Project project) {
        final Collection<String> aliases = new HashSet<String>();

        visitRegisterCoreContainerAliases(project, new DicAliasVisitor() {
            @Override
            public void visit(@NotNull PsiElement value, @NotNull String keyName) {
                aliases.add(keyName);
            }
        });

        return aliases;
    }

    public static void visitRegisterCoreContainerAliases(@NotNull Project project, @NotNull DicAliasVisitor visitor) {
        for (PhpClass phpClass : PhpElementsUtil.getClassesOrInterfaces(project, "Illuminate\\Foundation\\Application")) {
            Method registerMethod = phpClass.findMethodByName("registerCoreContainerAliases");
            if(registerMethod == null) {
                continue;
            }

            final Collection<Variable> aliases = new HashSet<Variable>();
            registerMethod.acceptChildren(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if(element instanceof Variable && ((Variable) element).isDeclaration() && "aliases".equals(((Variable) element).getName())) {
                        aliases.add((Variable) element);
                    }
                    super.visitElement(element);
                }
            });

            if(aliases.size() == 0) {
                continue;
            }

            for (Variable alias : aliases) {
                ArrayCreationExpression arrayCreation = PsiTreeUtil.getNextSiblingOfType(alias, ArrayCreationExpression.class);
                if(arrayCreation == null) {
                    continue;
                }

                Map<String, PsiElement> arrayCreationKeyMap = PhpElementsUtil.getArrayValueMap(arrayCreation);
                for (Map.Entry<String, PsiElement> entry : arrayCreationKeyMap.entrySet()) {
                    PsiElement value = entry.getValue();
                    visitor.visit(value, entry.getKey());
                }

            }

        }
    }

    interface DicAliasVisitor {
        void visit(@NotNull PsiElement value, @NotNull String keyName);
    }

}
