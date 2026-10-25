package com.asp.platform.config.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.asp.platform.shared.PublicWorkshopRef;

/// Lets controllers under `/api/v1/public/{slug}/` take a [PublicWorkshopRef] parameter: the workshop
/// [PublicWorkshopFilter] found for the slug.
final class PublicWorkshopArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PublicWorkshopRef.class.equals(parameter.getParameterType());
    }

    @Override
    public PublicWorkshopRef resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (webRequest.getAttribute(PublicPaths.WORKSHOP_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                instanceof PublicWorkshopRef workshop) {
            return workshop;
        }
        throw new IllegalStateException("PublicWorkshopRef is only available under " + PublicPaths.PREFIX);
    }
}
