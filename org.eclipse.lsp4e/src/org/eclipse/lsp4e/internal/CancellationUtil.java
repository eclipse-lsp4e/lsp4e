/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - Initial Implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

public final class CancellationUtil {

	private CancellationUtil() {
		// this class shouldn't be instantiated
	}

	public static boolean isRequestCancelledException(@Nullable Throwable throwable) {
		if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
			throwable = throwable.getCause();
		}
		if (throwable instanceof ResponseErrorException responseErrorException) {
			return isRequestCancelled(responseErrorException);
		}
		return throwable instanceof CancellationException;
	}

	private static boolean isRequestCancelled(ResponseErrorException responseErrorException) {
		ResponseError responseError = responseErrorException.getResponseError();
		return responseError != null
				&& (responseError.getCode() == ResponseErrorCode.RequestCancelled.getValue()
						|| responseError.getCode() == ResponseErrorCode.ServerCancelled.getValue());
	}

}
