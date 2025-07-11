/*
 * Copyright (c) 2018-present, Jim Kynde Meyer
 * All rights reserved.
 * <p>
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.intellij.lang.jsgraphql.schema

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.jsgraphql.ide.injection.GraphQLInjectedLanguage
import com.intellij.lang.jsgraphql.ide.resolve.GraphQLScopeDependency
import com.intellij.lang.jsgraphql.psi.GraphQLFile
import com.intellij.lang.jsgraphql.psi.GraphQLFragmentDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLOperationDefinition
import com.intellij.lang.jsgraphql.skipInTests
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CompositeModificationTracker
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.util.parentOfTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


/**
 * Tracks PSI changes that can affect declared GraphQL schemas.
 * For configuration only changes use [com.intellij.lang.jsgraphql.ide.config.GraphQLConfigProvider],
 * for scope changes use broader [GraphQLScopeDependency].
 */
@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class GraphQLSchemaContentTracker(private val project: Project, coroutineScope: CoroutineScope) : Disposable, ModificationTracker {

  companion object {
    private val LOG = logger<GraphQLSchemaContentTracker>()

    private const val EVENT_PUBLISH_TIMEOUT = 500

    @JvmStatic
    fun getInstance(project: Project): GraphQLSchemaContentTracker = project.service()
  }

  private val changeNotificationsFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val modificationTracker = CompositeModificationTracker(GraphQLScopeDependency.getInstance(project))

  init {
    PsiManager.getInstance(project).addPsiTreeChangeListener(PsiChangeListener(), this)

    skipInTests {
      coroutineScope.launch {
        changeNotificationsFlow.debounce(EVENT_PUBLISH_TIMEOUT.milliseconds).collect {
          notifySchemaContentChanged()
        }
      }
    }
  }

  fun update() {
    LOG.traceThrowable { Throwable("GraphQL schema cache invalidated") }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      updateModificationTracker()
      return
    }

    check(changeNotificationsFlow.tryEmit(Unit))
  }

  private fun notifySchemaContentChanged() {
    updateModificationTracker()
    project.messageBus.syncPublisher(GraphQLSchemaContentChangeListener.TOPIC).onSchemaChanged()
    DaemonCodeAnalyzer.getInstance(project).restart()
  }

  private fun updateModificationTracker() {
    modificationTracker.incModificationCount()
  }

  override fun getModificationCount(): Long {
    return modificationTracker.modificationCount
  }

  override fun dispose() {}

  /**
   * always consider the schema changed when editing an endpoint file
   * change in injection target
   * ignore the generic event which fires for all other cases above
   * if it's not the generic case, children have been replaced, e.g. using the commenter
   */
  private inner class PsiChangeListener : PsiTreeChangeAdapter() {
    private fun checkForSchemaChange(event: PsiTreeChangeEvent) {
      if (project.isDisposed) {
        return
      }

      if (event.file is GraphQLFile) {
        if (affectsGraphQLSchema(event)) {
          update()
        }
      }

      // TODO: check if it works as expected, looks like event.parent is not enough
      if (event.parent is PsiLanguageInjectionHost) {
        val injectionHelper = GraphQLInjectedLanguage.forElement(event.parent)
        if (injectionHelper != null && injectionHelper.isLanguageInjectionTarget(event.parent)) {
          // change in injection target
          update()
        }
      }
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
      checkForSchemaChange(event)
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
      checkForSchemaChange(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
      checkForSchemaChange(event)
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
      checkForSchemaChange(event)
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
      checkForSchemaChange(event)
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      if (event is PsiTreeChangeEventImpl) {
        if (!event.isGenericChange) {
          // ignore the generic event which fires for all other cases above
          // if it's not the generic case, children have been replaced, e.g. using the commenter
          checkForSchemaChange(event)
        }
      }
    }

    /**
     * Evaluates whether the change event can affect the associated GraphQL schema
     *
     * @param event the event that occurred
     * @return true if the change can affect the declared schema
     */
    private fun affectsGraphQLSchema(event: PsiTreeChangeEvent): Boolean {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.propertyName || PsiTreeChangeEvent.PROP_DIRECTORY_NAME == event.propertyName) {
        // renamed and moves are likely to affect schema blobs etc.
        return true
      }
      val elements = sequenceOf(event.parent, event.child, event.newChild, event.oldChild)
      for (element in elements) {
        if (element == null) {
          continue
        }

        val containingDeclaration = element
          .parentOfTypes(GraphQLOperationDefinition::class, GraphQLFragmentDefinition::class, withSelf = true)

        if (containingDeclaration != null) {
          // edits inside query, mutation, subscription, fragment etc. don't affect the schema
          return false
        }
      }
      // fallback to assume the schema can be affected by the edit
      return true
    }
  }
}
