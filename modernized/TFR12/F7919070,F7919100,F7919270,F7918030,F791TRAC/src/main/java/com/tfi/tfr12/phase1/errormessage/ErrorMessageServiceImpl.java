package com.tfi.tfr12.phase1.errormessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Replacement for legacy program F7918030.src. See {@link ErrorMessageService}
 * for the fallback search order and the not-found/repository-failure text rules.
 */
public final class ErrorMessageServiceImpl implements ErrorMessageService {

    @Override
    public ErrorMessageResult lookupMessage(ErrorMessageRequest request, MessageRepository repository, boolean cicsEnvironment) {
        if (request.alreadyFormatted()) {
            return new ErrorMessageResult(request.messageCode(), request.preformattedText());
        }

        String lang = (request.language() == null || request.language().isBlank()) ? "NO" : request.language();
        long orgId = request.organizationId();

        List<MessageKey> attempts = new ArrayList<>();
        attempts.add(new MessageKey(orgId, request.messageCode(), lang));
        if (orgId != 0) {
            attempts.add(new MessageKey(0, request.messageCode(), lang));
        }
        if (!"NO".equals(lang)) {
            attempts.add(new MessageKey(orgId, request.messageCode(), "NO"));
        }
        if (orgId != 0 && !"NO".equals(lang)) {
            attempts.add(new MessageKey(0, request.messageCode(), "NO"));
        }

        try {
            for (MessageKey key : attempts) {
                Optional<MessageRow> row = repository.find(key);
                if (row.isPresent()) {
                    String text = substituteVariables(row.get().messageText(), request.variables()).text();
                    return new ErrorMessageResult(request.messageCode(), text);
                }
            }
        } catch (MessageRepositoryFailureException e) {
            // Repository failure always yields the batch (comma) text, in every
            // environment - there is no CICS-specific text for this branch.
            return new ErrorMessageResult(request.messageCode(), NOT_FOUND_TEXT_BATCH);
        }

        String fallback = cicsEnvironment ? NOT_FOUND_TEXT_CICS : NOT_FOUND_TEXT_BATCH;
        return new ErrorMessageResult(request.messageCode(), fallback);
    }

    @Override
    public VariableSubstitutionResult substituteVariables(String template, List<MessageVariable> variables) {
        Map<String, String> values = new HashMap<>();
        for (MessageVariable v : variables) {
            values.put(v.key(), v.value() == null ? null : v.value().stripTrailing());
        }

        StringBuilder out = new StringBuilder();
        boolean unresolved = false;
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '&') {
                int hashIdx = template.indexOf('#', i + 1);
                if (hashIdx < 0) {
                    // Unterminated '&' (a "false start"): emit the rest literally.
                    out.append(template, i, n);
                    i = n;
                } else {
                    String key = template.substring(i + 1, hashIdx);
                    String value = values.get(key);
                    if (value != null && !value.isBlank()) {
                        out.append(value);
                    } else {
                        out.append('&').append(key).append('#');
                        unresolved = true;
                    }
                    i = hashIdx + 1;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return new VariableSubstitutionResult(out.toString(), unresolved);
    }
}
