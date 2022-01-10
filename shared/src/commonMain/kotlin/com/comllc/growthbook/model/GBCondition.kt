package com.comllc.growthbook.model

/*
    TODO - Targeting condition based on MongoDB query syntax.
    For details on parsing and evaluating these conditions, view the reference Typescript implementation
    https://github.com/growthbook/growthbook/tree/main/packages/sdk-js/src/mongrule.ts
 */

//enum class GBOperator(val oprator: String) {
//    inOp("\$in"),
//    ninOp("\$nin"),
//    gtOp("\$gt"),
//    gteOp("\$gte"),
//    ltOp("\$lt"),
//    lteOp("\$lte"),
//    regexOp("\$regex"),
//    neOp("\$ne"),
//    eqOp("\$eq"),
//    sizeOp("\$size"),
//    elemMatchOp("\$elemMatch"),
//    allOp("\$all"),
//    notOp("\$not"),
//    typeOp("\$type"),
//    existsOp("\$exists")
//}
//
//typealias GBVar = {"string", "number"}
//
//enum class GBVarType(val type: String) {
//    stringType("string"),
//    numberType("number"),
//    booleanType("boolean"),
//    arrayType("array"),
//    objectType("object"),
//    nullType("null"),
//    undefinedType("undefined")
//}
//
//
//enum class GBCondition {
//
//    $or: GBCondition[];
//};
//type NorCondition = {
//    $nor: GBCondition[];
//};
//type AndCondition = {
//    $and: GBCondition[];
//};
//type NotCondition = {
//    $not: GBCondition;
//};

class GBCondition {

}

typealias TestedObj = HashMap<String, Any>