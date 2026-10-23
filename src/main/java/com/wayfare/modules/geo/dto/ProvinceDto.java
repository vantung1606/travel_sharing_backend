package com.wayfare.modules.geo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDto {

    private String code;

    private String name;

    private String region;

    private List<String> aliases;
}
