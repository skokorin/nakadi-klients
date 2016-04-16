package org.zalando.nakadi.client.model

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

// Updated untill commit 7839be3  
// Compare

/**
 * The Event definition will be externalized in future versions of this document.
 * A basic payload of an Event. The actual schema is dependent on the information configured for the
 * EventType, as is its enforcement (see POST /event-types). Setting of metadata properties are
 * dependent on the configured enrichment as well.
 * For explanation on default configurations of validation and enrichment, see documentation of
 * `EventType.type`.
 * For concrete examples of what will be enforced by Nakadi see the objects BusinessEvent and
 * DataChangeEvent below.
 * @param eventType
 * @param additionalProperties Default value is true
 * @param title
 */
trait Event {
  def metadata(): Option[EventMetadata]
}

/**
 *
 * Metadata for this Event. Contains commons fields for both Business and DataChange Events. Most are enriched by Nakadi upon reception, but they in general MIGHT be set by the client.
 * @param eid Identifier of this Event. Clients are allowed to generate this and this SHOULD be guaranteed to be unique from the perspective of the producer. Consumers MIGHT use this value to assert uniqueness of reception of the Event.
 * @param eventType The EventType of this Event. This is enriched by Nakadi on reception of the Event based on the endpoint where the Producer sent the Event to. If provided MUST match the endpoint. Failure to do so will cause rejection of the Event.
 * @param occurredAt Timestamp of creation of the Event generated by the producer.
 * @param receivedAt Timestamp of the reception of the Event by Nakadi. This is enriched upon reception of the Event. If set by the producer Event will be rejected.
 * @param parentEids Event identifier of the Event that caused the generation of this Event. Set by the producer.
 * @param flowId The flow-id of the producer of this Event. As this is usually a HTTP header, this is enriched from the header into the metadata by Nakadi to avoid clients having to explicitly copy this.
 * @param metadata This Metadata contains common fields unrelated to Nakadi logic. Should be mainly enriched by the Consumer.
 *
 */
case class EventMetadata(
  eid: String,
  eventType: Option[EventType],
  occurredAt: String,
  receivedAt: Option[String],
  parentEids: Seq[String],
  flowId: Option[String],
  partition: Option[String])

/**
 * A Business Event. Usually represents a status transition in a Business process.
 *
 */
trait BusinessEvent extends Event {
}

/**
 * Indicators of a `DataChangeEvent`'s referred data type and the type of operations done on them.
 * @param dataType The datatype of the `DataChangeEvent`.
 * @param dataOp The type of operation executed on the entity. * C: Creation * U: Update * D: Deletion * S: Snapshot
 */
trait DataChangeEventQualifier {
  def dataType(): String
  def dataOperation(): DataOperation.Value
}

/**
 * A Data change Event. Represents a change on a resource.
 *
 * @param data The payload of the type
 * @param eventQualifier Indicators of a `DataChangeEvent`'s referred data type and the type of operations done on them.
 * @param metadata Metadata for this Event. Contains commons fields for both Business and DataChange Events. Most are enriched by Nakadi upon reception, but they in general MIGHT be set by the client
 */
case class DataChangeEvent[T](
  data: T,
  dataType: String,
  @JsonScalaEnumeration(classOf[DataOperationType]) dataOperation: DataOperation.Value,
  metadata: Option[EventMetadata]) extends DataChangeEventQualifier with Event

/**
 * @ param problemType An absolute URI that identifies the problem type. When dereferenced, it SHOULD provide human-readable API documentation for the problem type (e.g., using HTML). This Problem object is the same as provided by https://github.com/zalando/problem
 * @ param title A short, summary of the problem type. Written in English and readable for engineers (usually not suited for non technical stakeholders and not localized)
 * @ param status The HTTP status code generated by the origin server for this occurrence of the problem.
 * @ param detail A human readable explanation specific to this occurrence of the problem.
 * @ param instance An absolute URI that identifies the specific occurrence of the problem. It may or may not yield further information if dereferenced.
 */
case class Problem(
  problemType: String,
  title: String,
  status: Int,
  detail: Option[String],
  instance: Option[String])

case class Metrics(metrics: Map[String, Any])

/**
 * Partition information. Can be helpful when trying to start a stream using an unmanaged API. This information is not related to the state of the consumer clients.
 * @param  partition The partition's id.
 * @param  oldestAvailableOffset An offset of the oldest available Event in that partition. This value will be changing upon removal of Events from the partition by the background archiving/cleanup mechanism.
 * @param  newestAvailableOffset An offset of the newest available Event in that partition. This value will be changing upon reception of new events for this partition by Nakadi. This value can be used to construct a cursor when opening streams (see `GET /event-type/{name}/events` for details). Might assume the special name BEGIN, meaning a pointer to the offset of the oldest available event in the partition.
 */
case class Partition(
  partition: String,
  oldestAvailableOffset: String,
  newestAvailableOffset: String)
/**
 * @param partition Id of the partition pointed to by this cursor.
 * @param offset Offset of the event being pointed to.
 */
case class Cursor(
  partition: Integer,
  offset: Integer)

/**
 * One chunk of events in a stream. A batch consists of an array of `Event`s plus a `Cursor` pointing to the offset of the last Event in the stream. The size of the array of Event is limited by the parameters used to initialize a Stream. If acting as a keep alive message (see `GET /event-type/{name}/events`) the events array will be omitted. Sequential batches might repeat the cursor if no new events arrive.
 * @param cursor The cursor point to an event in the stream.
 * @param events The Event definition will be externalized in future versions of this document. A basic payload of an Event. The actual schema is dependent on the information configured for the EventType, as is its enforcement (see POST /event-types). Setting of metadata properties are dependent on the configured enrichment as well. For explanation on default configurations of validation and enrichment, see documentation of `EventType.type`. For concrete examples of what will be enforced by Nakadi see the objects
 * sEvent and DataChangeEvent below.
 */
case class EventStreamBatch[T <: Event](
  cursor: Cursor,
  events: Seq[T])

/**
 * An event type defines the schema and its runtime properties.
 * @param name Name of this EventType. Encodes the owner/responsible for this EventType. The name for the EventType SHOULD follow the pattern, but is not enforced 'stups_owning_application.event-type', for example 'gizig.price-change'. The components of the name are: * Organization: the organizational unit where the team is located; can be omitted. * Team name: name of the team responsible for owning application; can be omitted. * Owning application: SHOULD match the field owning_application; indicates * EventType name: name of this EventType; SHOULD end in ChangeEvent for DataChangeEvents; MUST be in the past tense for BusinessEvents. (TBD: how to deal with organizational changes? Should be reflected on the name of the EventType? Isn't it better to omit [organization:team] completely?)
 * @param owningApplication Indicator of the Application owning this `EventType`.
 * @param category Defines the category of this EventType. The value set will influence, if not set otherwise, the default set of validation-strategies, enrichment-strategies, and the effective_schema in the following way: - `undefined`: No predefined changes apply. `effective_schema` is exactly the same as the `EventTypeSchema`. Default validation_strategy for this `EventType` is `[{name: 'schema-validation'}]`. - `data`: Events of this category will be DataChangeEvents. `effective_schema` contains `metadata`, and adds fields `data_op` and `data_type`. The passed EventTypeSchema defines the schema of `data`. Default validation_strategy for this `EventType` is `[{name: 'datachange-schema-validation'}]`. - `business`: Events of this category will be BusinessEvents. `effective_schema` contains `metadata` and any additionally defined properties passed in the `EventTypeSchema` directly on top level of the Event. If name conflicts arise, creation of this EventType will be rejected. Default validation_strategy for this `EventType` is `[{name: 'schema-validation'}]`.
 * @param validationStrategies Determines the validation that has to be executed upon reception of Events of this type. Events are rejected if any of the rules fail (see details of Problem response on the Event publishing methods). Rule evaluation order is the same as in this array. If not explicitly set, default value will respect the definition of the `EventType.category`.
 * @param enrichmentStrategies Determines the enrichment to be performed on an Event upon reception. Enrichment is performed once upon reception (and after validation) of an Event and is only possible on fields that are not defined on the incoming Event. See documentation for the write operation for details on behaviour in case of unsuccessful enrichment.
 * @param partitionStrategy Determines how the assignment of the event to a Partition should be handled.
 * @param schema The schema for this EventType. This is expected to be a json schema in yaml format (other formats might be added in the future).
 * @param dataKeyFields Indicators of the path of the properties that constitute the primary key (identifier) of the data within this Event. If set MUST be a valid required field as defined in the schema. (TBD should be required? Is applicable only to both Business and DataChange Events?)
 * @param partitioningKeyFields Indicator of the field used for guaranteeing the ordering of Events of this type (used by the PartitionResolutionStrategy). If set MUST be a valid required field as defined in the schema.
 * @param statistics Statistics of this EventType used for optimization purposes. Internal use of these values might change over time. (TBD: measured statistics).
 *
 */
case class EventType(
  name: String,
  owningApplication: String,
  @JsonScalaEnumeration(classOf[EventTypeCategoryType]) category: EventTypeCategory.Value,
  @JsonScalaEnumeration(classOf[EventValidationStrategyType]) validationStrategies: Option[Seq[EventValidationStrategy.Value]],
  @JsonScalaEnumeration(classOf[EventEnrichmentStrategyType]) enrichmentStrategies: Seq[EventEnrichmentStrategy.Value],
  @JsonScalaEnumeration(classOf[PartitionStrategyType]) partitionStrategy: Option[PartitionStrategy.Value],
  schema: Option[EventTypeSchema],
  dataKeyFields: Option[Seq[String]],
  partitionKeyFields: Option[Seq[String]],
  statistics: Option[EventTypeStatistics])

/**
 * The schema for an EventType, expected to be a json schema in yaml
 * format (other formats might be added in the future).
 * @param type The type of schema definition (avro, json-schema, etc).
 * @param schema The schema as string in the syntax defined in the field type.
 * Failure to respect the syntax will fail any operation on an EventType.
 */
case class EventTypeSchema(
  @JsonProperty("type")@JsonScalaEnumeration(classOf[SchemaTypeType]) schemaType: SchemaType.Value, //Name is type (keyword in scala)
  schema: String)

/**
 * Operational statistics for an EventType. This data is generated by Nakadi
 * based on the runtime and might be used to guide changes in internal parameters.
 * @param expectedWriteRate - Write rate for events of this EventType. This rate encompasses all producers of this EventType for a Nakadi cluster. Measured in kb/minutes.
 * @param messageSize - Average message size for each Event of this EventType. Includes in the count the whole serialized form of the event, including metadata. Measured in bytes.
 * @param readParallelism - Amount of parallel readers (consumers) to this EventType.
 * @param writeParallelism - Amount of parallel writers (producers) to this EventType.
 *
 */
case class EventTypeStatistics(
  expectedWriteRate: Option[Int],
  messageSize: Option[Int],
  readParallelism: Option[Int],
  writeParallelism: Option[Int])

/**
 * Defines a rule for validation of an incoming Event. Rules might require additional parameters; see the `doc` field of the existing rules for details. See GET /registry/validation-strategies for a list of available rules.
 * @param name Name of the strategy.
 * @param doc Documentation for the validation.
 */
case object EventValidationStrategy extends Enumeration {
  type EventValidationStrategyxtends = Value
  val NONE = Value("None")
}
class EventValidationStrategyType extends TypeReference[EventValidationStrategy.type]

/**
 * Defines a rule for the resolution of incoming Events into partitions. Rules might require additional parameters; see the `doc` field of the existing rules for details. See `GET /registry/partition-strategies` for a list of available rules.
 * @param name Name of the strategy.
 * @param doc Documentation for the partition resolution.
 */
case object PartitionStrategy extends Enumeration {
  type PartitionResolutionStrategy = Value
  val HASH = Value("hash")
  val USER_DEFINED = Value("user_defined")
  val RANDOM = Value("random")
}
class PartitionStrategyType extends TypeReference[PartitionStrategy.type]

/**
 * Defines a rule for transformation of an incoming Event. No existing fields might be modified. In practice this is used to set automatically values in the Event upon reception (i.e. set a reception timestamp on the Event). Rules might require additional parameters; see the `doc` field of the existing rules for details. See GET /registry/enrichment-strategies for a list of available rules.
 * @param name Name of the strategy.
 * @param doc Documentation for the enrichment.
 */
case object EventEnrichmentStrategy extends Enumeration {
  type EventEnrichmentStrategy = Value
  val METADATA = Value("metadata_enrichment")
}
class EventEnrichmentStrategyType extends TypeReference[EventEnrichmentStrategy.type]

/**
 * A status corresponding to one individual Event's publishing attempt.
 * @param eid Eid of the corresponding item. Will be absent if missing on the incoming Event.
 * @param publishingStatus Indicator of the submission of the Event within a Batch. - SUBMITTED indicates successful submission, including commit on he underlying broker. - FAILED indicates the message submission was not possible and can be resubmitted if so desired. - ABORTED indicates that the submission of this item was not attempted any further due to a failure on another item in the batch.
 * @param step Indicator of the step in the pulbishing process this Event reached. In Items that FAILED means the step of the failure. - NONE indicates that nothing was yet attempted for the publishing of this Event. Should be present only in the case of aborting the publishing during the validation of another (previous) Event. - VALIDATING, ENRICHING, PARTITIONING and PUBLISHING indicate all the corresponding steps of the publishing process.
 * @param detail Human readable information about the failure on this item. Items that are not SUBMITTED should have a description.
 *
 */
case class BatchItemResponse(
  eid: Option[String],
  @JsonScalaEnumeration(classOf[BatchItemPublishingStatusType]) publishingStatus: BatchItemPublishingStatus.Value,
  @JsonScalaEnumeration(classOf[BatchItemStepType]) step: Option[BatchItemStep.Value],
  detail: Option[String])

/////////////////////////////////
// ENUMS ////////////////////////
/////////////////////////////////

/**
 * Identifier for the type of operation to executed on the entity. <br>
 * C: Creation <br>
 * U: Update <br>
 * D: Deletion <br>
 * S: Snapshot <br> <br>
 * Values = CREATE("C"), UPDATE("U"), DELETE("D"), SNAPSHOT("S")
 */

case object DataOperation extends Enumeration {
  type DataOperation = Value
  val CREATE = Value("C")
  val UPDATE = Value("U")
  val DELETE = Value("D")
  val SNAPSHOT = Value("S")

}
class DataOperationType extends TypeReference[DataOperation.type]

/**
 * Defines the category of an EventType. <br>
 * Values = UNDEFINED("undefined") DATA("data") BUSINESS("business")
 */
case object EventTypeCategory extends Enumeration {
  type EventTypeCategory = Value
  val UNDEFINED = Value("undefined")
  val DATA = Value("data")
  val BUSINESS = Value("business")

}
class EventTypeCategoryType extends TypeReference[EventTypeCategory.type]
/**
 * Indicator of the submission of the Event within a Batch. <br>
 * - SUBMITTED indicates successful submission, including commit on he underlying broker.<br>
 * - FAILED indicates the message submission was not possible and can be resubmitted if so desired.<br>
 * - ABORTED indicates that the submission of this item was not attempted any further due to a failure
 * on another item in the batch.<br> <br>
 * Values = SUBMITTED("SUBMITTED") FAILED("FAILED") ABORTED("ABORTED")
 */
case object BatchItemPublishingStatus extends Enumeration {
  type BatchItemPublishingStatus = Value
  val SUBMITTED = Value("SUBMITTED")
  val FAILED = Value("FAILED")
  val ABORTED = Value("ABORTED")
}

class BatchItemPublishingStatusType extends TypeReference[BatchItemPublishingStatus.type]

/**
 * Indicator of the step in the pulbishing process this Event reached.
 * In Items that FAILED means the step of the failure.
 * - NONE indicates that nothing was yet attempted for the publishing of this Event. Should be present
 * only in the case of aborting the publishing during the validation of another (previous) Event. <br>
 * - VALIDATING, ENRICHING, PARTITIONING and PUBLISHING indicate all the corresponding steps of the
 * publishing process. <br> <br>
 * Values = NONE("NONE"), VALIDATING("VALIDATING"), ENRICHING("ENRICHING"), PARTITIONING("PARTITIONING"), PUBLISHING("PUBLISHING")
 */
case object BatchItemStep extends Enumeration {
  type BatchItemStep = Value
  val NONE = Value("NONE")
  val VALIDATING = Value("VALIDATING")
  val ENRICHING = Value("ENRICHING")
  val PARTITIONING = Value("PARTITIONING")
  val PUBLISHING = Value("PUBLISHING")

}
class BatchItemStepType extends TypeReference[BatchItemStep.type]

case object SchemaType extends Enumeration {
  type SchemaType = Value
  val JSON = Value("json_schema")
}
class SchemaTypeType extends TypeReference[SchemaType.type]