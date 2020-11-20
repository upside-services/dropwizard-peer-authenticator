package com.getupside.dw.auth;

/**
 * <p>This package provides a stand-alone implementation of a DropWizard Authenticator interface that our principle REST service
 * can use to validate any requests coming to its /auth endpoint are themselves valid.</p>
 *
 * <p>That is to say, we don't just allow any old request to show up and try to get UserContextData responses with some random
 * key.  The requestor must itself be on a known list of authorized callers of this service.  The set of authorized peers
 * is assumed to come from one or more AWS SecretsManager coordinates, each coordinate containing one or more pairs of
 * username, passwords.</p>
 *
 */