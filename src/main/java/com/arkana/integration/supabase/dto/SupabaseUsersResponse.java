package com.arkana.integration.supabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SupabaseUsersResponse(List<SupabaseUser> users) {}
