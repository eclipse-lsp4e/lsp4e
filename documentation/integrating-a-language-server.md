## Properly integrating a language server with LSP4E

### General principle

Use the `org.eclipse.lsp4e.languageServer` extension in `plugin.xml` to define a language server (ie how to start it and connect to it) and to associate it with one or many content-types.

A server is started and receives `didOpen` when a matching document is shown in an editor or when a feature sends it a request.
Documents loaded without an editor (by builders, search, compare or other plug-ins) are only connected to servers declaring `connectFileBuffers="true"`.

### Examples

See [multiple examples in the test](../org.eclipse.lsp4e.test/plugin.xml).

### Tips and Tricks

* Use the `ProcessStreamConnectionProvider` if your LS is accessible as a process.

* To handle LSP Commands (e.g. associated with `CodeLens` messages) in the IDE, register a sub-class of `org.eclipse.lsp4e.command.LSPCommandHandler` in the `plugin.xml` file of your plug-in via the `org.eclipse.ui.handlers` extension. The handler has to be registered for the `commandId` equal to the `Command.command` attribute of an LSP message. Note that client side handlers will not be triggered, if the language server is capable of executing commands of the same `commandId`.