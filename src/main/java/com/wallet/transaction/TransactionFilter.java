package com.wallet.transaction;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TransactionFilter {
    private Integer page = 0;
    private Integer size = 20;
    private String type;
    private String status;
    private String dateFrom;
    private String dateTo;
}
