#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class GrowthBookGBExperiment, GrowthBookGBExperimentResult, GrowthBookGrowthBookSDK, GrowthBookSDKBuilder, GrowthBookGBSDKBuilderApp, GrowthBookGBFeature, GrowthBookGBFeatureResult, GrowthBookGBError, GrowthBookGBContext, GrowthBookKotlinThrowable, GrowthBookKotlinx_serialization_jsonJson, GrowthBookKotlinx_serialization_jsonJsonElement, GrowthBookGBExperimentCompanion, GrowthBookGBFeatureRule, GrowthBookGBFeatureCompanion, GrowthBookGBFeatureSource, GrowthBookGBFeatureRuleCompanion, GrowthBookKotlinEnumCompanion, GrowthBookKotlinEnum<E>, GrowthBookKotlinArray<T>, GrowthBookKotlinx_serialization_coreSerializersModule, GrowthBookKotlinx_serialization_jsonJsonConfiguration, GrowthBookKotlinx_serialization_jsonJsonDefault, GrowthBookKotlinx_serialization_jsonJsonElementCompanion, GrowthBookKotlinx_serialization_coreSerialKind, GrowthBookKotlinNothing;

@protocol GrowthBookNetworkDispatcher, GrowthBookKotlinx_serialization_coreKSerializer, GrowthBookKotlinComparable, GrowthBookKotlinx_serialization_coreDeserializationStrategy, GrowthBookKotlinx_serialization_coreSerializationStrategy, GrowthBookKotlinx_serialization_coreSerialFormat, GrowthBookKotlinx_serialization_coreStringFormat, GrowthBookKotlinx_serialization_coreEncoder, GrowthBookKotlinx_serialization_coreSerialDescriptor, GrowthBookKotlinx_serialization_coreDecoder, GrowthBookKotlinIterator, GrowthBookKotlinx_serialization_coreSerializersModuleCollector, GrowthBookKotlinKClass, GrowthBookKotlinx_serialization_coreCompositeEncoder, GrowthBookKotlinAnnotation, GrowthBookKotlinx_serialization_coreCompositeDecoder, GrowthBookKotlinKDeclarationContainer, GrowthBookKotlinKAnnotatedElement, GrowthBookKotlinKClassifier;

NS_ASSUME_NONNULL_BEGIN
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-warning-option"
#pragma clang diagnostic ignored "-Wincompatible-property-type"
#pragma clang diagnostic ignored "-Wnullability"

#pragma push_macro("_Nullable_result")
#if !__has_feature(nullability_nullable_result)
#undef _Nullable_result
#define _Nullable_result _Nullable
#endif

__attribute__((swift_name("KotlinBase")))
@interface GrowthBookBase : NSObject
- (instancetype)init __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
+ (void)initialize __attribute__((objc_requires_super));
@end;

@interface GrowthBookBase (GrowthBookBaseCopying) <NSCopying>
@end;

__attribute__((swift_name("KotlinMutableSet")))
@interface GrowthBookMutableSet<ObjectType> : NSMutableSet<ObjectType>
@end;

__attribute__((swift_name("KotlinMutableDictionary")))
@interface GrowthBookMutableDictionary<KeyType, ObjectType> : NSMutableDictionary<KeyType, ObjectType>
@end;

@interface NSError (NSErrorGrowthBookKotlinException)
@property (readonly) id _Nullable kotlinException;
@end;

__attribute__((swift_name("KotlinNumber")))
@interface GrowthBookNumber : NSNumber
- (instancetype)initWithChar:(char)value __attribute__((unavailable));
- (instancetype)initWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
- (instancetype)initWithShort:(short)value __attribute__((unavailable));
- (instancetype)initWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
- (instancetype)initWithInt:(int)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
- (instancetype)initWithLong:(long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
- (instancetype)initWithLongLong:(long long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
- (instancetype)initWithFloat:(float)value __attribute__((unavailable));
- (instancetype)initWithDouble:(double)value __attribute__((unavailable));
- (instancetype)initWithBool:(BOOL)value __attribute__((unavailable));
- (instancetype)initWithInteger:(NSInteger)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
+ (instancetype)numberWithChar:(char)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
+ (instancetype)numberWithShort:(short)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
+ (instancetype)numberWithInt:(int)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
+ (instancetype)numberWithLong:(long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
+ (instancetype)numberWithLongLong:(long long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
+ (instancetype)numberWithFloat:(float)value __attribute__((unavailable));
+ (instancetype)numberWithDouble:(double)value __attribute__((unavailable));
+ (instancetype)numberWithBool:(BOOL)value __attribute__((unavailable));
+ (instancetype)numberWithInteger:(NSInteger)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
@end;

__attribute__((swift_name("KotlinByte")))
@interface GrowthBookByte : GrowthBookNumber
- (instancetype)initWithChar:(char)value;
+ (instancetype)numberWithChar:(char)value;
@end;

__attribute__((swift_name("KotlinUByte")))
@interface GrowthBookUByte : GrowthBookNumber
- (instancetype)initWithUnsignedChar:(unsigned char)value;
+ (instancetype)numberWithUnsignedChar:(unsigned char)value;
@end;

__attribute__((swift_name("KotlinShort")))
@interface GrowthBookShort : GrowthBookNumber
- (instancetype)initWithShort:(short)value;
+ (instancetype)numberWithShort:(short)value;
@end;

__attribute__((swift_name("KotlinUShort")))
@interface GrowthBookUShort : GrowthBookNumber
- (instancetype)initWithUnsignedShort:(unsigned short)value;
+ (instancetype)numberWithUnsignedShort:(unsigned short)value;
@end;

__attribute__((swift_name("KotlinInt")))
@interface GrowthBookInt : GrowthBookNumber
- (instancetype)initWithInt:(int)value;
+ (instancetype)numberWithInt:(int)value;
@end;

__attribute__((swift_name("KotlinUInt")))
@interface GrowthBookUInt : GrowthBookNumber
- (instancetype)initWithUnsignedInt:(unsigned int)value;
+ (instancetype)numberWithUnsignedInt:(unsigned int)value;
@end;

__attribute__((swift_name("KotlinLong")))
@interface GrowthBookLong : GrowthBookNumber
- (instancetype)initWithLongLong:(long long)value;
+ (instancetype)numberWithLongLong:(long long)value;
@end;

__attribute__((swift_name("KotlinULong")))
@interface GrowthBookULong : GrowthBookNumber
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value;
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value;
@end;

__attribute__((swift_name("KotlinFloat")))
@interface GrowthBookFloat : GrowthBookNumber
- (instancetype)initWithFloat:(float)value;
+ (instancetype)numberWithFloat:(float)value;
@end;

__attribute__((swift_name("KotlinDouble")))
@interface GrowthBookDouble : GrowthBookNumber
- (instancetype)initWithDouble:(double)value;
+ (instancetype)numberWithDouble:(double)value;
@end;

__attribute__((swift_name("KotlinBoolean")))
@interface GrowthBookBoolean : GrowthBookNumber
- (instancetype)initWithBool:(BOOL)value;
+ (instancetype)numberWithBool:(BOOL)value;
@end;

__attribute__((swift_name("SDKBuilder")))
@interface GrowthBookSDKBuilder : GrowthBookBase
- (instancetype)initWithApiKey:(NSString *)apiKey hostURL:(NSString *)hostURL attributes:(NSDictionary<NSString *, id> *)attributes trackingCallback:(void (^)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *))trackingCallback __attribute__((swift_name("init(apiKey:hostURL:attributes:trackingCallback:)"))) __attribute__((objc_designated_initializer));
- (GrowthBookGrowthBookSDK *)initialize __attribute__((swift_name("initialize()")));
- (GrowthBookSDKBuilder *)setEnabledIsEnabled:(BOOL)isEnabled __attribute__((swift_name("setEnabled(isEnabled:)")));
- (GrowthBookSDKBuilder *)setForcedVariationsForcedVariations:(NSDictionary<NSString *, GrowthBookInt *> *)forcedVariations __attribute__((swift_name("setForcedVariations(forcedVariations:)")));
- (GrowthBookSDKBuilder *)setQAModeIsEnabled:(BOOL)isEnabled __attribute__((swift_name("setQAMode(isEnabled:)")));
@property (readonly) NSString *apiKey __attribute__((swift_name("apiKey")));
@property (readonly) NSDictionary<NSString *, id> *attributes __attribute__((swift_name("attributes")));
@property (readonly) NSString *hostURL __attribute__((swift_name("hostURL")));
@property (readonly) void (^trackingCallback)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *) __attribute__((swift_name("trackingCallback")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBSDKBuilderApp")))
@interface GrowthBookGBSDKBuilderApp : GrowthBookSDKBuilder
- (instancetype)initWithApiKey:(NSString *)apiKey hostURL:(NSString *)hostURL attributes:(NSDictionary<NSString *, id> *)attributes trackingCallback:(void (^)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *))trackingCallback __attribute__((swift_name("init(apiKey:hostURL:attributes:trackingCallback:)"))) __attribute__((objc_designated_initializer));
- (GrowthBookGrowthBookSDK *)initialize __attribute__((swift_name("initialize()")));
- (GrowthBookSDKBuilder *)setNetworkDispatcherNetworkDispatcher:(id<GrowthBookNetworkDispatcher>)networkDispatcher __attribute__((swift_name("setNetworkDispatcher(networkDispatcher:)")));
- (GrowthBookGBSDKBuilderApp *)setRefreshHandlerRefreshHandler:(void (^)(GrowthBookBoolean *))refreshHandler __attribute__((swift_name("setRefreshHandler(refreshHandler:)")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBSDKBuilderJAVA")))
@interface GrowthBookGBSDKBuilderJAVA : GrowthBookSDKBuilder
- (instancetype)initWithApiKey:(NSString *)apiKey hostURL:(NSString *)hostURL attributes:(NSDictionary<NSString *, id> *)attributes features:(GrowthBookMutableDictionary<NSString *, GrowthBookGBFeature *> *)features trackingCallback:(void (^)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *))trackingCallback __attribute__((swift_name("init(apiKey:hostURL:attributes:features:trackingCallback:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithApiKey:(NSString *)apiKey hostURL:(NSString *)hostURL attributes:(NSDictionary<NSString *, id> *)attributes trackingCallback:(void (^)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *))trackingCallback __attribute__((swift_name("init(apiKey:hostURL:attributes:trackingCallback:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
- (GrowthBookGrowthBookSDK *)initialize __attribute__((swift_name("initialize()")));
@property (readonly) GrowthBookMutableDictionary<NSString *, GrowthBookGBFeature *> *features __attribute__((swift_name("features")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GrowthBookSDK")))
@interface GrowthBookGrowthBookSDK : GrowthBookBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (GrowthBookGBFeatureResult *)featureId:(NSString *)id __attribute__((swift_name("feature(id:)")));
- (void)featuresFetchFailedError:(GrowthBookGBError *)error isRemote:(BOOL)isRemote __attribute__((swift_name("featuresFetchFailed(error:isRemote:)")));
- (void)featuresFetchedSuccessfullyFeatures:(GrowthBookMutableDictionary<NSString *, GrowthBookGBFeature *> *)features isRemote:(BOOL)isRemote __attribute__((swift_name("featuresFetchedSuccessfully(features:isRemote:)")));
- (GrowthBookMutableDictionary<NSString *, GrowthBookGBFeature *> *)getFeatures __attribute__((swift_name("getFeatures()")));
- (GrowthBookGBContext *)getGBContext __attribute__((swift_name("getGBContext()")));
- (void)refreshCache __attribute__((swift_name("refreshCache()")));
- (GrowthBookGBExperimentResult *)runExperiment:(GrowthBookGBExperiment *)experiment __attribute__((swift_name("run(experiment:)")));
@end;

__attribute__((swift_name("NetworkDispatcher")))
@protocol GrowthBookNetworkDispatcher
@required
- (void)consumeGETRequestRequest:(NSString *)request onSuccess:(void (^)(NSString *))onSuccess onError:(void (^)(GrowthBookKotlinThrowable *))onError __attribute__((swift_name("consumeGETRequest(request:onSuccess:onError:)")));
@property (readonly) GrowthBookKotlinx_serialization_jsonJson *JSONParser __attribute__((swift_name("JSONParser")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBError")))
@interface GrowthBookGBError : GrowthBookBase
- (instancetype)initWithError:(GrowthBookKotlinThrowable * _Nullable)error __attribute__((swift_name("init(error:)"))) __attribute__((objc_designated_initializer));
@property NSString *errorMessage __attribute__((swift_name("errorMessage")));
@property NSString *stackTrace __attribute__((swift_name("stackTrace")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBContext")))
@interface GrowthBookGBContext : GrowthBookBase
- (instancetype)initWithApiKey:(NSString *)apiKey hostURL:(NSString *)hostURL enabled:(BOOL)enabled attributes:(NSDictionary<NSString *, id> *)attributes forcedVariations:(NSDictionary<NSString *, GrowthBookInt *> *)forcedVariations qaMode:(BOOL)qaMode trackingCallback:(void (^)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *))trackingCallback __attribute__((swift_name("init(apiKey:hostURL:enabled:attributes:forcedVariations:qaMode:trackingCallback:)"))) __attribute__((objc_designated_initializer));
@property (readonly) NSString *apiKey __attribute__((swift_name("apiKey")));
@property (readonly) NSDictionary<NSString *, id> *attributes __attribute__((swift_name("attributes")));
@property (readonly) BOOL enabled __attribute__((swift_name("enabled")));
@property (readonly) NSDictionary<NSString *, GrowthBookInt *> *forcedVariations __attribute__((swift_name("forcedVariations")));
@property (readonly) NSString *hostURL __attribute__((swift_name("hostURL")));
@property (readonly) BOOL qaMode __attribute__((swift_name("qaMode")));
@property (readonly) void (^trackingCallback)(GrowthBookGBExperiment *, GrowthBookGBExperimentResult *) __attribute__((swift_name("trackingCallback")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBExperiment")))
@interface GrowthBookGBExperiment : GrowthBookBase
- (instancetype)initWithKey:(NSString *)key variations:(NSArray<GrowthBookKotlinx_serialization_jsonJsonElement *> *)variations namespace:(NSArray<GrowthBookKotlinx_serialization_jsonJsonElement *> * _Nullable)namespace_ hashAttribute:(NSString * _Nullable)hashAttribute weights:(NSArray<GrowthBookFloat *> * _Nullable)weights active:(BOOL)active coverage:(GrowthBookFloat * _Nullable)coverage condition:(GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable)condition force:(GrowthBookInt * _Nullable)force __attribute__((swift_name("init(key:variations:namespace:hashAttribute:weights:active:coverage:condition:force:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) GrowthBookGBExperimentCompanion *companion __attribute__((swift_name("companion")));
@property BOOL active __attribute__((swift_name("active")));
@property GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable condition __attribute__((swift_name("condition")));
@property GrowthBookFloat * _Nullable coverage __attribute__((swift_name("coverage")));
@property GrowthBookInt * _Nullable force __attribute__((swift_name("force")));
@property (readonly) NSString * _Nullable hashAttribute __attribute__((swift_name("hashAttribute")));
@property (readonly) NSString *key __attribute__((swift_name("key")));
@property (readonly, getter=namespace) NSArray<GrowthBookKotlinx_serialization_jsonJsonElement *> * _Nullable namespace_ __attribute__((swift_name("namespace_")));
@property (readonly) NSArray<GrowthBookKotlinx_serialization_jsonJsonElement *> *variations __attribute__((swift_name("variations")));
@property NSArray<GrowthBookFloat *> * _Nullable weights __attribute__((swift_name("weights")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBExperiment.Companion")))
@interface GrowthBookGBExperimentCompanion : GrowthBookBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) GrowthBookGBExperimentCompanion *shared __attribute__((swift_name("shared")));
- (id<GrowthBookKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBExperimentResult")))
@interface GrowthBookGBExperimentResult : GrowthBookBase
- (instancetype)initWithInExperiment:(BOOL)inExperiment variationId:(int32_t)variationId value:(id)value hashAttribute:(NSString * _Nullable)hashAttribute hashValue:(NSString * _Nullable)hashValue __attribute__((swift_name("init(inExperiment:variationId:value:hashAttribute:hashValue:)"))) __attribute__((objc_designated_initializer));
@property (readonly) NSString * _Nullable hashAttribute __attribute__((swift_name("hashAttribute")));
@property (readonly) NSString * _Nullable hashValue __attribute__((swift_name("hashValue")));
@property (readonly) BOOL inExperiment __attribute__((swift_name("inExperiment")));
@property (readonly) id value __attribute__((swift_name("value")));
@property (readonly) int32_t variationId __attribute__((swift_name("variationId")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBFeature")))
@interface GrowthBookGBFeature : GrowthBookBase
- (instancetype)initWithDefaultValue:(GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable)defaultValue rules:(NSArray<GrowthBookGBFeatureRule *> * _Nullable)rules __attribute__((swift_name("init(defaultValue:rules:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) GrowthBookGBFeatureCompanion *companion __attribute__((swift_name("companion")));
@property (readonly) GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable defaultValue __attribute__((swift_name("defaultValue")));
@property (readonly) NSArray<GrowthBookGBFeatureRule *> * _Nullable rules __attribute__((swift_name("rules")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBFeature.Companion")))
@interface GrowthBookGBFeatureCompanion : GrowthBookBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) GrowthBookGBFeatureCompanion *shared __attribute__((swift_name("shared")));
- (id<GrowthBookKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBFeatureResult")))
@interface GrowthBookGBFeatureResult : GrowthBookBase
- (instancetype)initWithValue:(id _Nullable)value on:(BOOL)on off:(BOOL)off source:(GrowthBookGBFeatureSource *)source experiment:(GrowthBookGBExperiment * _Nullable)experiment experimentResult:(GrowthBookGBExperimentResult * _Nullable)experimentResult __attribute__((swift_name("init(value:on:off:source:experiment:experimentResult:)"))) __attribute__((objc_designated_initializer));
@property (readonly) GrowthBookGBExperiment * _Nullable experiment __attribute__((swift_name("experiment")));
@property (readonly) GrowthBookGBExperimentResult * _Nullable experimentResult __attribute__((swift_name("experimentResult")));
@property (readonly) BOOL off __attribute__((swift_name("off")));
@property (readonly) BOOL on __attribute__((swift_name("on")));
@property (readonly) GrowthBookGBFeatureSource *source __attribute__((swift_name("source")));
@property (readonly) id _Nullable value __attribute__((swift_name("value")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBFeatureRule")))
@interface GrowthBookGBFeatureRule : GrowthBookBase
- (instancetype)initWithCondition:(GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable)condition coverage:(GrowthBookFloat * _Nullable)coverage force:(GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable)force variations:(NSMutableArray<GrowthBookKotlinx_serialization_jsonJsonElement *> * _Nullable)variations key:(NSString * _Nullable)key weights:(NSArray<GrowthBookFloat *> * _Nullable)weights namespace:(NSArray<GrowthBookKotlinx_serialization_jsonJsonElement *> * _Nullable)namespace_ hashAttribute:(NSString * _Nullable)hashAttribute __attribute__((swift_name("init(condition:coverage:force:variations:key:weights:namespace:hashAttribute:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) GrowthBookGBFeatureRuleCompanion *companion __attribute__((swift_name("companion")));
@property (readonly) GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable condition __attribute__((swift_name("condition")));
@property (readonly) GrowthBookFloat * _Nullable coverage __attribute__((swift_name("coverage")));
@property (readonly) GrowthBookKotlinx_serialization_jsonJsonElement * _Nullable force __attribute__((swift_name("force")));
@property (readonly) NSString * _Nullable hashAttribute __attribute__((swift_name("hashAttribute")));
@property (readonly) NSString * _Nullable key __attribute__((swift_name("key")));
@property (readonly, getter=namespace) NSArray<GrowthBookKotlinx_serialization_jsonJsonElement *> * _Nullable namespace_ __attribute__((swift_name("namespace_")));
@property (readonly) NSMutableArray<GrowthBookKotlinx_serialization_jsonJsonElement *> * _Nullable variations __attribute__((swift_name("variations")));
@property (readonly) NSArray<GrowthBookFloat *> * _Nullable weights __attribute__((swift_name("weights")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBFeatureRule.Companion")))
@interface GrowthBookGBFeatureRuleCompanion : GrowthBookBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) GrowthBookGBFeatureRuleCompanion *shared __attribute__((swift_name("shared")));
- (id<GrowthBookKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end;

__attribute__((swift_name("KotlinComparable")))
@protocol GrowthBookKotlinComparable
@required
- (int32_t)compareToOther:(id _Nullable)other __attribute__((swift_name("compareTo(other:)")));
@end;

__attribute__((swift_name("KotlinEnum")))
@interface GrowthBookKotlinEnum<E> : GrowthBookBase <GrowthBookKotlinComparable>
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) GrowthBookKotlinEnumCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(E)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *name __attribute__((swift_name("name")));
@property (readonly) int32_t ordinal __attribute__((swift_name("ordinal")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("GBFeatureSource")))
@interface GrowthBookGBFeatureSource : GrowthBookKotlinEnum<GrowthBookGBFeatureSource *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) GrowthBookGBFeatureSource *unknownfeature __attribute__((swift_name("unknownfeature")));
@property (class, readonly) GrowthBookGBFeatureSource *defaultvalue __attribute__((swift_name("defaultvalue")));
@property (class, readonly) GrowthBookGBFeatureSource *force __attribute__((swift_name("force")));
@property (class, readonly) GrowthBookGBFeatureSource *experiment __attribute__((swift_name("experiment")));
+ (GrowthBookKotlinArray<GrowthBookGBFeatureSource *> *)values __attribute__((swift_name("values()")));
@end;

__attribute__((swift_name("KotlinThrowable")))
@interface GrowthBookKotlinThrowable : GrowthBookBase
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(GrowthBookKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(GrowthBookKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
- (GrowthBookKotlinArray<NSString *> *)getStackTrace __attribute__((swift_name("getStackTrace()")));
- (void)printStackTrace __attribute__((swift_name("printStackTrace()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) GrowthBookKotlinThrowable * _Nullable cause __attribute__((swift_name("cause")));
@property (readonly) NSString * _Nullable message __attribute__((swift_name("message")));
- (NSError *)asError __attribute__((swift_name("asError()")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreSerialFormat")))
@protocol GrowthBookKotlinx_serialization_coreSerialFormat
@required
@property (readonly) GrowthBookKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreStringFormat")))
@protocol GrowthBookKotlinx_serialization_coreStringFormat <GrowthBookKotlinx_serialization_coreSerialFormat>
@required
- (id _Nullable)decodeFromStringDeserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer string:(NSString *)string __attribute__((swift_name("decodeFromString(deserializer:string:)")));
- (NSString *)encodeToStringSerializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeToString(serializer:value:)")));
@end;

__attribute__((swift_name("Kotlinx_serialization_jsonJson")))
@interface GrowthBookKotlinx_serialization_jsonJson : GrowthBookBase <GrowthBookKotlinx_serialization_coreStringFormat>
- (instancetype)initWithConfiguration:(GrowthBookKotlinx_serialization_jsonJsonConfiguration *)configuration serializersModule:(GrowthBookKotlinx_serialization_coreSerializersModule *)serializersModule __attribute__((swift_name("init(configuration:serializersModule:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) GrowthBookKotlinx_serialization_jsonJsonDefault *companion __attribute__((swift_name("companion")));
- (id _Nullable)decodeFromJsonElementDeserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer element:(GrowthBookKotlinx_serialization_jsonJsonElement *)element __attribute__((swift_name("decodeFromJsonElement(deserializer:element:)")));
- (id _Nullable)decodeFromStringDeserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer string:(NSString *)string __attribute__((swift_name("decodeFromString(deserializer:string:)")));
- (GrowthBookKotlinx_serialization_jsonJsonElement *)encodeToJsonElementSerializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeToJsonElement(serializer:value:)")));
- (NSString *)encodeToStringSerializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeToString(serializer:value:)")));
- (GrowthBookKotlinx_serialization_jsonJsonElement *)parseToJsonElementString:(NSString *)string __attribute__((swift_name("parseToJsonElement(string:)")));
@property (readonly) GrowthBookKotlinx_serialization_jsonJsonConfiguration *configuration __attribute__((swift_name("configuration")));
@property (readonly) GrowthBookKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end;

__attribute__((swift_name("Kotlinx_serialization_jsonJsonElement")))
@interface GrowthBookKotlinx_serialization_jsonJsonElement : GrowthBookBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
@property (class, readonly, getter=companion) GrowthBookKotlinx_serialization_jsonJsonElementCompanion *companion __attribute__((swift_name("companion")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreSerializationStrategy")))
@protocol GrowthBookKotlinx_serialization_coreSerializationStrategy
@required
- (void)serializeEncoder:(id<GrowthBookKotlinx_serialization_coreEncoder>)encoder value:(id _Nullable)value __attribute__((swift_name("serialize(encoder:value:)")));
@property (readonly) id<GrowthBookKotlinx_serialization_coreSerialDescriptor> descriptor __attribute__((swift_name("descriptor")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreDeserializationStrategy")))
@protocol GrowthBookKotlinx_serialization_coreDeserializationStrategy
@required
- (id _Nullable)deserializeDecoder:(id<GrowthBookKotlinx_serialization_coreDecoder>)decoder __attribute__((swift_name("deserialize(decoder:)")));
@property (readonly) id<GrowthBookKotlinx_serialization_coreSerialDescriptor> descriptor __attribute__((swift_name("descriptor")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreKSerializer")))
@protocol GrowthBookKotlinx_serialization_coreKSerializer <GrowthBookKotlinx_serialization_coreSerializationStrategy, GrowthBookKotlinx_serialization_coreDeserializationStrategy>
@required
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinEnumCompanion")))
@interface GrowthBookKotlinEnumCompanion : GrowthBookBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) GrowthBookKotlinEnumCompanion *shared __attribute__((swift_name("shared")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinArray")))
@interface GrowthBookKotlinArray<T> : GrowthBookBase
+ (instancetype)arrayWithSize:(int32_t)size init:(T _Nullable (^)(GrowthBookInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (T _Nullable)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (id<GrowthBookKotlinIterator>)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(T _Nullable)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreSerializersModule")))
@interface GrowthBookKotlinx_serialization_coreSerializersModule : GrowthBookBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (void)dumpToCollector:(id<GrowthBookKotlinx_serialization_coreSerializersModuleCollector>)collector __attribute__((swift_name("dumpTo(collector:)")));
- (id<GrowthBookKotlinx_serialization_coreKSerializer> _Nullable)getContextualKClass:(id<GrowthBookKotlinKClass>)kClass typeArgumentsSerializers:(NSArray<id<GrowthBookKotlinx_serialization_coreKSerializer>> *)typeArgumentsSerializers __attribute__((swift_name("getContextual(kClass:typeArgumentsSerializers:)")));
- (id<GrowthBookKotlinx_serialization_coreSerializationStrategy> _Nullable)getPolymorphicBaseClass:(id<GrowthBookKotlinKClass>)baseClass value:(id)value __attribute__((swift_name("getPolymorphic(baseClass:value:)")));
- (id<GrowthBookKotlinx_serialization_coreDeserializationStrategy> _Nullable)getPolymorphicBaseClass:(id<GrowthBookKotlinKClass>)baseClass serializedClassName:(NSString * _Nullable)serializedClassName __attribute__((swift_name("getPolymorphic(baseClass:serializedClassName:)")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_serialization_jsonJsonConfiguration")))
@interface GrowthBookKotlinx_serialization_jsonJsonConfiguration : GrowthBookBase
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) BOOL allowSpecialFloatingPointValues __attribute__((swift_name("allowSpecialFloatingPointValues")));
@property (readonly) BOOL allowStructuredMapKeys __attribute__((swift_name("allowStructuredMapKeys")));
@property (readonly) NSString *classDiscriminator __attribute__((swift_name("classDiscriminator")));
@property (readonly) BOOL coerceInputValues __attribute__((swift_name("coerceInputValues")));
@property (readonly) BOOL encodeDefaults __attribute__((swift_name("encodeDefaults")));
@property (readonly) BOOL explicitNulls __attribute__((swift_name("explicitNulls")));
@property (readonly) BOOL ignoreUnknownKeys __attribute__((swift_name("ignoreUnknownKeys")));
@property (readonly) BOOL isLenient __attribute__((swift_name("isLenient")));
@property (readonly) BOOL prettyPrint __attribute__((swift_name("prettyPrint")));
@property (readonly) NSString *prettyPrintIndent __attribute__((swift_name("prettyPrintIndent")));
@property (readonly) BOOL useAlternativeNames __attribute__((swift_name("useAlternativeNames")));
@property (readonly) BOOL useArrayPolymorphism __attribute__((swift_name("useArrayPolymorphism")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_serialization_jsonJson.Default")))
@interface GrowthBookKotlinx_serialization_jsonJsonDefault : GrowthBookKotlinx_serialization_jsonJson
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithConfiguration:(GrowthBookKotlinx_serialization_jsonJsonConfiguration *)configuration serializersModule:(GrowthBookKotlinx_serialization_coreSerializersModule *)serializersModule __attribute__((swift_name("init(configuration:serializersModule:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
+ (instancetype)default_ __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) GrowthBookKotlinx_serialization_jsonJsonDefault *shared __attribute__((swift_name("shared")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_serialization_jsonJsonElement.Companion")))
@interface GrowthBookKotlinx_serialization_jsonJsonElementCompanion : GrowthBookBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) GrowthBookKotlinx_serialization_jsonJsonElementCompanion *shared __attribute__((swift_name("shared")));
- (id<GrowthBookKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreEncoder")))
@protocol GrowthBookKotlinx_serialization_coreEncoder
@required
- (id<GrowthBookKotlinx_serialization_coreCompositeEncoder>)beginCollectionDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor collectionSize:(int32_t)collectionSize __attribute__((swift_name("beginCollection(descriptor:collectionSize:)")));
- (id<GrowthBookKotlinx_serialization_coreCompositeEncoder>)beginStructureDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("beginStructure(descriptor:)")));
- (void)encodeBooleanValue:(BOOL)value __attribute__((swift_name("encodeBoolean(value:)")));
- (void)encodeByteValue:(int8_t)value __attribute__((swift_name("encodeByte(value:)")));
- (void)encodeCharValue:(unichar)value __attribute__((swift_name("encodeChar(value:)")));
- (void)encodeDoubleValue:(double)value __attribute__((swift_name("encodeDouble(value:)")));
- (void)encodeEnumEnumDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)enumDescriptor index:(int32_t)index __attribute__((swift_name("encodeEnum(enumDescriptor:index:)")));
- (void)encodeFloatValue:(float)value __attribute__((swift_name("encodeFloat(value:)")));
- (id<GrowthBookKotlinx_serialization_coreEncoder>)encodeInlineInlineDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)inlineDescriptor __attribute__((swift_name("encodeInline(inlineDescriptor:)")));
- (void)encodeIntValue:(int32_t)value __attribute__((swift_name("encodeInt(value:)")));
- (void)encodeLongValue:(int64_t)value __attribute__((swift_name("encodeLong(value:)")));
- (void)encodeNotNullMark __attribute__((swift_name("encodeNotNullMark()")));
- (void)encodeNull __attribute__((swift_name("encodeNull()")));
- (void)encodeNullableSerializableValueSerializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeNullableSerializableValue(serializer:value:)")));
- (void)encodeSerializableValueSerializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeSerializableValue(serializer:value:)")));
- (void)encodeShortValue:(int16_t)value __attribute__((swift_name("encodeShort(value:)")));
- (void)encodeStringValue:(NSString *)value __attribute__((swift_name("encodeString(value:)")));
@property (readonly) GrowthBookKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreSerialDescriptor")))
@protocol GrowthBookKotlinx_serialization_coreSerialDescriptor
@required
- (NSArray<id<GrowthBookKotlinAnnotation>> *)getElementAnnotationsIndex:(int32_t)index __attribute__((swift_name("getElementAnnotations(index:)")));
- (id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)getElementDescriptorIndex:(int32_t)index __attribute__((swift_name("getElementDescriptor(index:)")));
- (int32_t)getElementIndexName:(NSString *)name __attribute__((swift_name("getElementIndex(name:)")));
- (NSString *)getElementNameIndex:(int32_t)index __attribute__((swift_name("getElementName(index:)")));
- (BOOL)isElementOptionalIndex:(int32_t)index __attribute__((swift_name("isElementOptional(index:)")));
@property (readonly) NSArray<id<GrowthBookKotlinAnnotation>> *annotations __attribute__((swift_name("annotations")));
@property (readonly) int32_t elementsCount __attribute__((swift_name("elementsCount")));
@property (readonly) BOOL isInline __attribute__((swift_name("isInline")));
@property (readonly) BOOL isNullable __attribute__((swift_name("isNullable")));
@property (readonly) GrowthBookKotlinx_serialization_coreSerialKind *kind __attribute__((swift_name("kind")));
@property (readonly) NSString *serialName __attribute__((swift_name("serialName")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreDecoder")))
@protocol GrowthBookKotlinx_serialization_coreDecoder
@required
- (id<GrowthBookKotlinx_serialization_coreCompositeDecoder>)beginStructureDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("beginStructure(descriptor:)")));
- (BOOL)decodeBoolean __attribute__((swift_name("decodeBoolean()")));
- (int8_t)decodeByte __attribute__((swift_name("decodeByte()")));
- (unichar)decodeChar __attribute__((swift_name("decodeChar()")));
- (double)decodeDouble __attribute__((swift_name("decodeDouble()")));
- (int32_t)decodeEnumEnumDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)enumDescriptor __attribute__((swift_name("decodeEnum(enumDescriptor:)")));
- (float)decodeFloat __attribute__((swift_name("decodeFloat()")));
- (id<GrowthBookKotlinx_serialization_coreDecoder>)decodeInlineInlineDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)inlineDescriptor __attribute__((swift_name("decodeInline(inlineDescriptor:)")));
- (int32_t)decodeInt __attribute__((swift_name("decodeInt()")));
- (int64_t)decodeLong __attribute__((swift_name("decodeLong()")));
- (BOOL)decodeNotNullMark __attribute__((swift_name("decodeNotNullMark()")));
- (GrowthBookKotlinNothing * _Nullable)decodeNull __attribute__((swift_name("decodeNull()")));
- (id _Nullable)decodeNullableSerializableValueDeserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer __attribute__((swift_name("decodeNullableSerializableValue(deserializer:)")));
- (id _Nullable)decodeSerializableValueDeserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer __attribute__((swift_name("decodeSerializableValue(deserializer:)")));
- (int16_t)decodeShort __attribute__((swift_name("decodeShort()")));
- (NSString *)decodeString __attribute__((swift_name("decodeString()")));
@property (readonly) GrowthBookKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end;

__attribute__((swift_name("KotlinIterator")))
@protocol GrowthBookKotlinIterator
@required
- (BOOL)hasNext __attribute__((swift_name("hasNext()")));
- (id _Nullable)next __attribute__((swift_name("next()")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreSerializersModuleCollector")))
@protocol GrowthBookKotlinx_serialization_coreSerializersModuleCollector
@required
- (void)contextualKClass:(id<GrowthBookKotlinKClass>)kClass provider:(id<GrowthBookKotlinx_serialization_coreKSerializer> (^)(NSArray<id<GrowthBookKotlinx_serialization_coreKSerializer>> *))provider __attribute__((swift_name("contextual(kClass:provider:)")));
- (void)contextualKClass:(id<GrowthBookKotlinKClass>)kClass serializer:(id<GrowthBookKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("contextual(kClass:serializer:)")));
- (void)polymorphicBaseClass:(id<GrowthBookKotlinKClass>)baseClass actualClass:(id<GrowthBookKotlinKClass>)actualClass actualSerializer:(id<GrowthBookKotlinx_serialization_coreKSerializer>)actualSerializer __attribute__((swift_name("polymorphic(baseClass:actualClass:actualSerializer:)")));
- (void)polymorphicDefaultBaseClass:(id<GrowthBookKotlinKClass>)baseClass defaultDeserializerProvider:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy> _Nullable (^)(NSString * _Nullable))defaultDeserializerProvider __attribute__((swift_name("polymorphicDefault(baseClass:defaultDeserializerProvider:)")));
- (void)polymorphicDefaultDeserializerBaseClass:(id<GrowthBookKotlinKClass>)baseClass defaultDeserializerProvider:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy> _Nullable (^)(NSString * _Nullable))defaultDeserializerProvider __attribute__((swift_name("polymorphicDefaultDeserializer(baseClass:defaultDeserializerProvider:)")));
- (void)polymorphicDefaultSerializerBaseClass:(id<GrowthBookKotlinKClass>)baseClass defaultSerializerProvider:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy> _Nullable (^)(id))defaultSerializerProvider __attribute__((swift_name("polymorphicDefaultSerializer(baseClass:defaultSerializerProvider:)")));
@end;

__attribute__((swift_name("KotlinKDeclarationContainer")))
@protocol GrowthBookKotlinKDeclarationContainer
@required
@end;

__attribute__((swift_name("KotlinKAnnotatedElement")))
@protocol GrowthBookKotlinKAnnotatedElement
@required
@end;

__attribute__((swift_name("KotlinKClassifier")))
@protocol GrowthBookKotlinKClassifier
@required
@end;

__attribute__((swift_name("KotlinKClass")))
@protocol GrowthBookKotlinKClass <GrowthBookKotlinKDeclarationContainer, GrowthBookKotlinKAnnotatedElement, GrowthBookKotlinKClassifier>
@required
- (BOOL)isInstanceValue:(id _Nullable)value __attribute__((swift_name("isInstance(value:)")));
@property (readonly) NSString * _Nullable qualifiedName __attribute__((swift_name("qualifiedName")));
@property (readonly) NSString * _Nullable simpleName __attribute__((swift_name("simpleName")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreCompositeEncoder")))
@protocol GrowthBookKotlinx_serialization_coreCompositeEncoder
@required
- (void)encodeBooleanElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(BOOL)value __attribute__((swift_name("encodeBooleanElement(descriptor:index:value:)")));
- (void)encodeByteElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int8_t)value __attribute__((swift_name("encodeByteElement(descriptor:index:value:)")));
- (void)encodeCharElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(unichar)value __attribute__((swift_name("encodeCharElement(descriptor:index:value:)")));
- (void)encodeDoubleElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(double)value __attribute__((swift_name("encodeDoubleElement(descriptor:index:value:)")));
- (void)encodeFloatElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(float)value __attribute__((swift_name("encodeFloatElement(descriptor:index:value:)")));
- (id<GrowthBookKotlinx_serialization_coreEncoder>)encodeInlineElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("encodeInlineElement(descriptor:index:)")));
- (void)encodeIntElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int32_t)value __attribute__((swift_name("encodeIntElement(descriptor:index:value:)")));
- (void)encodeLongElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int64_t)value __attribute__((swift_name("encodeLongElement(descriptor:index:value:)")));
- (void)encodeNullableSerializableElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index serializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeNullableSerializableElement(descriptor:index:serializer:value:)")));
- (void)encodeSerializableElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index serializer:(id<GrowthBookKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeSerializableElement(descriptor:index:serializer:value:)")));
- (void)encodeShortElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int16_t)value __attribute__((swift_name("encodeShortElement(descriptor:index:value:)")));
- (void)encodeStringElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(NSString *)value __attribute__((swift_name("encodeStringElement(descriptor:index:value:)")));
- (void)endStructureDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("endStructure(descriptor:)")));
- (BOOL)shouldEncodeElementDefaultDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("shouldEncodeElementDefault(descriptor:index:)")));
@property (readonly) GrowthBookKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end;

__attribute__((swift_name("KotlinAnnotation")))
@protocol GrowthBookKotlinAnnotation
@required
@end;

__attribute__((swift_name("Kotlinx_serialization_coreSerialKind")))
@interface GrowthBookKotlinx_serialization_coreSerialKind : GrowthBookBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@end;

__attribute__((swift_name("Kotlinx_serialization_coreCompositeDecoder")))
@protocol GrowthBookKotlinx_serialization_coreCompositeDecoder
@required
- (BOOL)decodeBooleanElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeBooleanElement(descriptor:index:)")));
- (int8_t)decodeByteElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeByteElement(descriptor:index:)")));
- (unichar)decodeCharElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeCharElement(descriptor:index:)")));
- (int32_t)decodeCollectionSizeDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeCollectionSize(descriptor:)")));
- (double)decodeDoubleElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeDoubleElement(descriptor:index:)")));
- (int32_t)decodeElementIndexDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeElementIndex(descriptor:)")));
- (float)decodeFloatElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeFloatElement(descriptor:index:)")));
- (id<GrowthBookKotlinx_serialization_coreDecoder>)decodeInlineElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeInlineElement(descriptor:index:)")));
- (int32_t)decodeIntElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeIntElement(descriptor:index:)")));
- (int64_t)decodeLongElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeLongElement(descriptor:index:)")));
- (id _Nullable)decodeNullableSerializableElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index deserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer previousValue:(id _Nullable)previousValue __attribute__((swift_name("decodeNullableSerializableElement(descriptor:index:deserializer:previousValue:)")));
- (BOOL)decodeSequentially __attribute__((swift_name("decodeSequentially()")));
- (id _Nullable)decodeSerializableElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index deserializer:(id<GrowthBookKotlinx_serialization_coreDeserializationStrategy>)deserializer previousValue:(id _Nullable)previousValue __attribute__((swift_name("decodeSerializableElement(descriptor:index:deserializer:previousValue:)")));
- (int16_t)decodeShortElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeShortElement(descriptor:index:)")));
- (NSString *)decodeStringElementDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeStringElement(descriptor:index:)")));
- (void)endStructureDescriptor:(id<GrowthBookKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("endStructure(descriptor:)")));
@property (readonly) GrowthBookKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end;

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinNothing")))
@interface GrowthBookKotlinNothing : GrowthBookBase
@end;

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
