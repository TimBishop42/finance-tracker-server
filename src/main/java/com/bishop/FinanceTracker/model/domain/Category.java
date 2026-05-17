package com.bishop.FinanceTracker.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Data
@Entity
@Table(name = "category")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String categoryName;
    private Long createDate;
}
