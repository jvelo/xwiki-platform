/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.gwt.wysiwyg.client;

import org.xwiki.gwt.dom.client.JavaScriptObject;
import org.xwiki.gwt.dom.client.Range;
import org.xwiki.gwt.dom.client.Selection;
import org.xwiki.gwt.user.client.Config;
import org.xwiki.gwt.user.client.NativeActionHandler;
import org.xwiki.gwt.user.client.internal.DefaultConfig;
import org.xwiki.gwt.user.client.ui.rta.RichTextArea;
import org.xwiki.gwt.user.client.ui.rta.cmd.Command;
import org.xwiki.gwt.user.client.ui.rta.cmd.CommandManagerApi;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * This class exposes a {@link WysiwygEditor} to the native JavaScript code.
 * 
 * @version $Id$
 */
public class WysiwygEditorApi
{
    /**
     * The command used to submit the value of the rich text area.
     */
    public static final Command SUBMIT = new Command("submit");

    /**
     * The property of the JavaScript object returned by {@link #getSelectionRange()} that holds the reference to the
     * DOM node where the selection starts.
     */
    private static final String START_CONTAINER = "startContainer";

    /**
     * The property of the JavaScript object returned by {@link #getSelectionRange()} that holds the offset within the
     * {@link #START_CONTAINER}.
     */
    private static final String START_OFFSET = "startOffset";

    /**
     * The property of the JavaScript object returned by {@link #getSelectionRange()} that holds the reference to the
     * DOM node where the selection ends.
     */
    private static final String END_CONTAINER = "endContainer";

    /**
     * The property of the JavaScript object returned by {@link #getSelectionRange()} that holds the offset within the
     * {@link #END_CONTAINER}.
     */
    private static final String END_OFFSET = "endOffset";

    /**
     * The underlying {@link WysiwygEditor} which is exposed in native JavaScript code.
     */
    private WysiwygEditor editor;

    /**
     * The JavaScript object that exposes the command manager used by the rich text area.
     */
    private CommandManagerApi commandManagerApi;

    /**
     * Creates a new {@link WysiwygEditor} based on the given configuration object.
     * 
     * @param jsConfig the {@link JavaScriptObject} used to configure the newly created editor
     */
    public WysiwygEditorApi(JavaScriptObject jsConfig)
    {
        if (!isRichTextEditingSupported()) {
            return;
        }

        Config config = new DefaultConfig(jsConfig);

        // Get the element that will be replaced by the WYSIWYG editor.
        Element hook = DOM.getElementById(config.getParameter("hookId"));
        if (hook == null) {
            return;
        }

        // Prepare the DOM by creating a container for the editor.
        final Element container = DOM.createDiv();
        String containerId = hook.getId() + "_container" + Math.round(Math.random() * 1000);
        container.setId(containerId);
        hook.getParentElement().insertBefore(container, hook);

        editor = this.getEditorFactory().newEditor(config);

        // Attach the editor to the browser's document.
        if (editor.getConfig().isDebugMode()) {
            RootPanel.get(containerId).add(new WysiwygEditorDebugger(editor));
        } else {
            RootPanel.get(containerId).add(editor.getUI());
        }

        // Cleanup when the window is closed. This way the HTML form elements generated on the server preserve their
        // index and thus can be cached by the browser.
        Window.addCloseHandler(new CloseHandler<Window>()
        {
            public void onClose(CloseEvent<Window> event)
            {
                if (editor != null) {
                    editor.destroy();
                }
                if (container.getParentNode() != null) {
                    container.getParentNode().removeChild(container);
                }
            }
        });
    }

    /**
     * @return the editor factory to use to create the editor.
     */
    protected WysiwygEditorFactory getEditorFactory()
    {
        return StandaloneWysiwygEditorFactory.getInstance();
    }

    /**
     * @return the underlying editor.
     * @see {@link #editor}
     */
    protected WysiwygEditor getEditor()
    {
        return this.getEditor();
    }

    /**
     * @return {@code true} if the current browser supports rich text editing, {@code false} otherwise
     */
    public static boolean isRichTextEditingSupported()
    {
        RichTextArea textArea = new RichTextArea(null);
        return textArea.getFormatter() != null;
    }

    /**
     * Releases the editor so that it can be garbage collected before the page is unloaded. Call this method before the
     * editor is physically detached from the DOM document.
     */
    public void release()
    {
        if (editor != null) {
            // Logical detach.
            Widget container = editor.getUI();
            while (container.getParent() != null) {
                container = container.getParent();
            }
            RootPanel.detachNow(container);
            editor = null;
        }
    }

    /**
     * @return the plain HTML text area element used by the editor
     */
    public Element getPlainTextArea()
    {
        return editor == null || !editor.getConfig().isTabbed() ? null : editor.getPlainTextEditor().getTextArea()
            .getElement();
    }

    /**
     * @return the rich text area element used by the editor
     */
    public Element getRichTextArea()
    {
        return editor == null ? null : editor.getRichTextEditor().getTextArea().getElement();
    }

    /**
     * @return a JavaScript object that exposes the command manager used by the rich text area
     */
    public CommandManagerApi getCommandManagerApi()
    {
        if (commandManagerApi == null) {
            commandManagerApi =
                CommandManagerApi.newInstance(editor.getRichTextEditor().getTextArea().getCommandManager());
        }
        return commandManagerApi;
    }

    /**
     * Creates an action handler that wraps the given JavaScript function and registers it for the specified action.
     * 
     * @param actionName the name of the action to listen to
     * @param jsHandler the JavaScript function to be called when the specified action occurs
     * @return the registration for the event, to be used for removing the handler
     */
    public HandlerRegistration addActionHandler(String actionName, JavaScriptObject jsHandler)
    {
        if (editor != null) {
            return editor.getRichTextEditor().getTextArea()
                .addActionHandler(actionName, new NativeActionHandler(jsHandler));
        }
        return null;
    }

    /**
     * @param name the name of a configuration parameter
     * @return the value of the specified editor configuration parameter
     */
    public String getParameter(String name)
    {
        return editor.getConfigurationSource().getParameter(name);
    }

    /**
     * @return the list of editor configuration parameters
     */
    public JsArrayString getParameterNames()
    {
        JsArrayString parameterNames = JavaScriptObject.createArray().cast();
        for (String parameterName : editor.getConfigurationSource().getParameterNames()) {
            parameterNames.push(parameterName);
        }
        return parameterNames;
    }

    /**
     * Focuses or blurs the WYSIWYG editor.
     * 
     * @param focused {@code true} to focus the WYSIWYG editor, {@code false} to blur it
     */
    public void setFocus(boolean focused)
    {
        if (editor.getRichTextEditor().getTextArea().isEnabled()) {
            editor.getRichTextEditor().getTextArea().setFocus(focused);
        } else {
            editor.getPlainTextEditor().getTextArea().setFocus(focused);
        }
    }

    /**
     * @return the rich text area's selection range
     */
    public JavaScriptObject getSelectionRange()
    {
        RichTextArea textArea = editor.getRichTextEditor().getTextArea();
        if (textArea.isAttached()) {
            Selection selection = textArea.getDocument().getSelection();
            if (selection.getRangeCount() > 0) {
                Range range = selection.getRangeAt(0);
                JavaScriptObject jsRange = (JavaScriptObject) JavaScriptObject.createObject();
                jsRange.set(START_CONTAINER, range.getStartContainer());
                jsRange.set(START_OFFSET, range.getStartOffset());
                jsRange.set(END_CONTAINER, range.getEndContainer());
                jsRange.set(END_OFFSET, range.getEndOffset());
                return jsRange;
            }
        }
        return null;
    }

    /**
     * Sets the rich text area's selection range.
     * 
     * @param jsRange a JavaScript object that has these properties: {@code startContainer}, {@code startOffset},
     *            {@code endContainer} and {@code endOffset}
     */
    public void setSelectionRange(JavaScriptObject jsRange)
    {
        RichTextArea textArea = editor.getRichTextEditor().getTextArea();
        if (jsRange != null && textArea.isAttached()) {
            Selection selection = textArea.getDocument().getSelection();
            Range range = textArea.getDocument().createRange();
            range.setStart((Node) jsRange.get(START_CONTAINER), (Integer) jsRange.get(START_OFFSET));
            range.setEnd((Node) jsRange.get(END_CONTAINER), (Integer) jsRange.get(END_OFFSET));
            selection.removeAllRanges();
            selection.addRange(range);
        }
    }

    /**
     * Publishes the JavaScript API that can be used to create and control {@link WysiwygEditor}s.
     */
    public static native void publish()
    /*-{
        // We can't use directly the WysiwygEditorApi constructor because currently there's no way to access (as in save
        // a reference to) the GWT instance methods without having an instance.
        $wnd.WysiwygEditor = function(config) {
            if (typeof config == 'object') {
                this.instance = @org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::new(Lorg/xwiki/gwt/dom/client/JavaScriptObject;)(config);
            }
        }
        $wnd.WysiwygEditor.prototype.release = function() {
            this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::release()();
        }
        $wnd.WysiwygEditor.prototype.getPlainTextArea = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getPlainTextArea()();
        }
        $wnd.WysiwygEditor.prototype.getRichTextArea = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getRichTextArea()();
        }
        $wnd.WysiwygEditor.prototype.getCommandManager = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getCommandManagerApi()();
        }
        $wnd.WysiwygEditor.prototype.addActionHandler = function(actionName, handler) {
            var registration = this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::addActionHandler(Ljava/lang/String;Lorg/xwiki/gwt/dom/client/JavaScriptObject;)('' + actionName, handler);
            return function() {
                if (registration) {
                    registration.@com.google.gwt.event.shared.HandlerRegistration::removeHandler()();
                    registration = null;
                }
            };
        }
        $wnd.WysiwygEditor.prototype.getParameter = function(name) {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getParameter(Ljava/lang/String;)('' + name);
        }
        $wnd.WysiwygEditor.prototype.getParameterNames = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getParameterNames()();
        }
        $wnd.WysiwygEditor.prototype.setFocus = function(focused) {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::setFocus(Z)(!!focused);
        }
        $wnd.WysiwygEditor.prototype.getSelectionRange = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getSelectionRange()();
        }
        $wnd.WysiwygEditor.prototype.setSelectionRange = function(range) {
            this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::setSelectionRange(Lorg/xwiki/gwt/dom/client/JavaScriptObject;)(range);
        }
    }-*/;
}
