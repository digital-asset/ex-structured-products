/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import * as React from 'react';
import * as UICore from '@da/ui-core';

// ------------------------------------------------------------------------------------------------
// Schema version (all config files will need to export this name)
// ------------------------------------------------------------------------------------------------
export const version = {
  schema: 'navigator-config',
  major: 2,
  minor: 0,
};

export function theme(userId, party, role) {
    return (party == "Issuer" ? { documentBackground: "#344a83" } :
           party == "Intermediary" ? { documentBackground: "#4c566e" } : //, colorPrimary: ["#C9C8CA", "White"], colorSecondary: ["#4f93de", "White"] }: //Grey700
           party == "Client" ? { documentBackground: "Grey" } :
           { documentBackground: "#800000" });
  };
   
var formatTime = function(timestamp) { return timestamp.substring(0, 10) + " " + timestamp.substring(11, 16); };
var formatDate = function(timestamp) { return timestamp.substring(0, 10); };
var month_names =["Jan","Feb","Mar",
                      "Apr","May","Jun",
                      "Jul","Aug","Sep",
                      "Oct","Nov","Dec"];
var formatDateIntoDDMMMYYYY = function(date) { return date.substring(8,10) + "-" + month_names[date.substring(5,7)-1] + "-" + date.substring(0,4); };
var temp = function() {
    console.log(document)
    var link = document.createElement("a");
    link.textContent = "SAVE AS CSV";
    link.download = "file.csv";
    //link.href = "data:text/csv,h1;All Questions\n"
    var x = document.getElementsByClassName("sc-iQKALj bUqlEJ");
    x.appendChild(link);
    location.reload();

    //document.body.appendChild(link);
    //alert("asdasd")
}

var createColumn = function(key, title, projection, width = 100, weight = 0, alignment = "left", sortable = true) {
    var createCell = function (data) {
        let input = {
            argument: UICore.DamlLfValue.toJSON(data.rowData.argument),
            template: data.rowData.template,
            id: data.rowData.id
        }
        return { type: 'text', value: projection(input) };
    };

    return {
        key: key,
        title: title,
        createCell: createCell,
        sortable: sortable,
        width: width,
        weight: weight,
        alignment: alignment
    }
};

var roles = {
    type: 'table-view',
    title: "Roles",
    source: { type: 'contracts', filter: [ { field: "template.id", value: "Role" } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
    columns: [
        createColumn("id", "ID", x => x.id, 20),
        createColumn("index1", "Index 1", x => Object.values(x.argument)[0], 50),
    ]
},

marketData = {
    type: 'table-view',
    title: "Market Data",
    source: { type: 'contracts', filter: [ { field: "template.id", value: "MarketData" } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
    columns: [
        createColumn("id", "ID", x => x.id, 20),
        createColumn("index1", "Index 1", x => x.argument.index1Price.underlying + ": " + (x.argument.index1Price.priceCCY.currency=="JPY"?"¥ ":"$ ") + x.argument.index1Price.priceCCY.price, 50),
        createColumn("index2", "Index 2", x => x.argument.index2Price.underlying + ": " + (x.argument.index2Price.priceCCY.currency=="JPY"?"¥ ":"$ ") + x.argument.index2Price.priceCCY.price, 50),
        createColumn("source", "Source", x => x.argument.source, 30),
        createColumn("publishDate", "Publish Date", x => formatDateIntoDDMMMYYYY(x.argument.publishDate.substring(0,10)), 50)   //.replace(new RegExp("-", 'g'), "/")
    ]
},

trading = {
    type: 'table-view',
    title: "Trading",
    source: { type: 'contracts', filter: [ { field: "template.id", value: "IntermediaryTradingRole" } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
    columns: [
        createColumn("trader", "Trader", x => x.argument.intermediary, 40),
        createColumn("observers", "Observers", x => (x.argument.issuer + ", " + x.argument.regulator), 40)
    ]
},

tradeDetails = {
    type: 'table-view',
    title: "Trade Details",
    source: { type: 'contracts', filter: [ { field: "template.id", value: "Trade" } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
    columns: [
        createColumn("id", "ID", x => x.id, 20),
        createColumn("type", "Event Type", x => x.template.id.includes("Proposal")?"Proposal":"Trade", 20),
        createColumn("tradeId", "Trade ID", x => x.argument.tradeId, 30),
        createColumn("productId", "Product ID", x => x.argument.terms.productId, 30),
        createColumn("notional", "Notional", x => (x.argument.terms.currency=="JPY"?"¥ ":"$ ") + x.argument.terms.notional, 50),
        createColumn("issuer", "Issuer", x => x.argument.issuerInfo.accountOwner, 40),
        createColumn("buyer", "Buyer", x => x.template.id.includes("Proposal")?x.argument.buyer:x.argument.buyerInfo.accountOwner, 40),
        createColumn("terms", "Terms", x => "Click Here", 20),
    ]
},

events = {
    type: 'table-view',
    title: "Events",
    source: { type: 'contracts', filter: [ { field: "template.id", value: "Event" } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
    columns: [
        createColumn("id", "ID", x => x.id, 10),
        createColumn("type", "Event Type", x => x.template.id.includes("KnockOut")? "Knock-Out":"Coupon", 10),
        createColumn("tradeId", "Trade ID", x => x.argument.tradeId, 10),
        createColumn("productId", "Product ID", x => x.argument.productId, 10),
        createColumn("interestRate", "InterestRate", x => x.template.id.includes("KnockOut")? "Redemption":(x.argument.interestRate*100)+" %", 30),
        createColumn("paymentDate", "PaymentDate", x => x.template.id.includes("KnockOut")? x.argument.redemptionDate.substring(0,10).replace(new RegExp("-", 'g'), "/"):x.argument.interestPaymentDate.substring(0,10).replace(new RegExp("-", 'g'), "/"), 30),
        createColumn("index1", "ClosingPrice 1", x => x.argument.closingPriceIndex1.underlying + ": " + (x.argument.closingPriceIndex1.priceCCY.currency=="JPY"?"¥ ":"$ ") + x.argument.closingPriceIndex1.priceCCY.price, 50),
        createColumn("strikeOrKO1", "Strike/KO Price 1", x => x.template.id.includes("KnockOut")? x.argument.closingPriceIndex1.underlying + ": " + (x.argument.index1KO.currency=="JPY"?"¥ ":"$ ") + x.argument.index1KO.price :
                                                          x.argument.closingPriceIndex1.underlying + ": " + (x.argument.index1CouponStrike.currency=="JPY"?"¥ ":"$ ") + x.argument.index1CouponStrike.price, 50),
        createColumn("index2", "ClosingPrice 2", x => x.argument.closingPriceIndex2.underlying + ": " + (x.argument.closingPriceIndex2.priceCCY.currency=="JPY"?"¥ ":"$ ") + x.argument.closingPriceIndex2.priceCCY.price, 50),
        createColumn("strikeOrKO2", "Strike/KO Price 2", x => x.template.id.includes("KnockOut")? x.argument.closingPriceIndex2.underlying + ": " + (x.argument.index2KO.currency=="JPY"?"¥ ":"$ ") + x.argument.index2KO.price :
                                                          x.argument.closingPriceIndex2.underlying + ": " + (x.argument.index2CouponStrike.currency=="JPY"?"¥ ":"$ ") + x.argument.index2CouponStrike.price, 50),
        // createColumn("paymentAmount", "Payment Amount", x => x.template.id.includes("KnockOut")? x.argument.redemptionAmount:x.argument.interestPaymentAmount, 40)
    ]
},

paymentInstructions = {
    type: 'table-view',
    title: "Payment Instructions",
    source: { type: 'contracts', filter: [ { field: "template.id", value: "PaymentInstructions" } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
    columns: [
        createColumn("id", "ID", x => x.id, 15),
        createColumn("transactionReference", "TransactionRef", x => x.argument.transactionReference, 40),
        createColumn("amount", "Amount", x => (x.argument.currency=="JPY"?"¥ ":"$ ") + x.argument.amount, 45),
        createColumn("date", "PaymentDate", x => x.argument.paymentDate.substring(0,10).replace(new RegExp("-", 'g'), "/"), 25),
        createColumn("payerInfo", "Payer (BIC/IBAN)", x => x.argument.payerDetails.accountOwner + " (" + x.argument.payerDetails.bic + "/" + x.argument.payerDetails.iban + ")", 250),
        createColumn("payeeInfo", "Payee (BIC/IBAN)", x => x.argument.payeeDetails.accountOwner + " (" + x.argument.payeeDetails.bic + "/" + x.argument.payeeDetails.iban + ")", 250),
        // createColumn("payerBIC", "BIC (Payer)", x => x.argument.payerDetails.bic, 40),
        // createColumn("payerIBAN", "IBAN (Payer)", x => x.argument.payerDetails.iban, 40),
        // createColumn("payee", "Payee", x => x.argument.payeeDetails.accountOwner, 25),
        // createColumn("payeeBIC", "BIC (Payee)", x => x.argument.payeeDetails.bic, 50),
        // createColumn("payeeIBAN", "IBAN (Payee)", x => x.argument.payeeDetails.iban, 50)
    ]
};
;

export const customViews = (userId, party, role) => {
    
    // party == "AM" ? { 
    //     rolesAndRelationships: rolesAndRelationships,
    //     standingAllocationInstructions: standingAllocationInstructions,
    //     fundAccounts: fundAccounts,
    //     dealAllegeNotifications: dealAllegeNotifications,
    //     allegedDealsAM: allegedDealsAM,
    //     matchedDealsAM: matchedDealsAM,
    //     unmatchedDealsAM: unmatchedDealsAM,
    //     allocations: allocations,
    //     settlementObligationsAM: settlementObligationsAM,    
    //     readyToSettleConfirmationsAMGCLC: readyToSettleConfirmationsAMGCLC
    // } :
    
    // party == "EP" ? { 
    //     rolesAndRelationships: rolesAndRelationships,
    //     brokerClearingAccounts: brokerClearingAccounts,
    //     dealAllegeNotifications: dealAllegeNotifications,
    //     allegedDealsEP: allegedDealsEP,
    //     matchedDealsEP: matchedDealsEP,
    //     unmatchedDealsEP: unmatchedDealsEP,
    //     allocations: allocations,
    //     settlementObligationsEP: settlementObligationsEP,
    //     readyToSettleConfirmationsEPCP: readyToSettleConfirmationsEPCP
    // } :

    // party == "GC" ? { 
    //     rolesAndRelationships: rolesAndRelationships,
    //     fundAccounts: fundAccounts,
    //     subcustodialAccounts: subcustodialAccounts,
    //     settlementObligationsGC: settlementObligationsGC,
    //     readyToSettleConfirmationsAMGCLC: readyToSettleConfirmationsAMGCLC
    // } :

    // party == "LC" ? { 
    //     rolesAndRelationships: rolesAndRelationships,
    //     subcustodialAccounts: subcustodialAccounts,
    //     settlementAccounts: settlementAccounts,
    //     settlementObligationsLC: settlementObligationsLC,
    //     readyToSettleConfirmationsAMGCLC: readyToSettleConfirmationsAMGCLC,
    //     SI_CCASS: SI_CCASS
    // } :

    // party == "CP" ? { 
    //     rolesAndRelationships: rolesAndRelationships,
    //     brokerClearingAccounts: brokerClearingAccounts,
    //     settlementAccounts: settlementAccounts,
    //     settlementObligationsCP: settlementObligationsCP,
    //     readyToSettleConfirmationsEPCP: readyToSettleConfirmationsEPCP,
    //     SI_CCASS: SI_CCASS
    // } :
    
    // party == "CCASS" ? { 
    //     SI_CCASS: SI_CCASS
    // } :

    var tabs = { 
        //roles: roles,
        marketData: marketData,
        tradeDetails: tradeDetails,
        events: events,
        paymentInstructions: paymentInstructions
    };

    if (party == "Intermediary"){
        tabs.trading = trading;
    };

    return tabs;

};

