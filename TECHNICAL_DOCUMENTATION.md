# Court Scheduler Service - Technical Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Functionality](#core-functionality)
4. [Business Rules](#business-rules)
5. [API Endpoints](#api-endpoints)
6. [Rota File Processing](#rota-file-processing)
7. [Data Models](#data-models)
8. [Technical Implementation](#technical-implementation)
9. [Configuration](#configuration)
10. [Integration Points](#integration-points)

---

## Overview

### Purpose
The Court Scheduler Service is a microservice designed to manage court schedules, hearing slots, and judicial allocations for HM Courts & Tribunals Service (HMCTS). The system processes  files from the ROTA system for judiciary updates , creates and manages court schedules, allocates hearing slots, and provides comprehensive scheduling capabilities for court operations.

### Key Capabilities
- **Court Schedule Management**: Create, update, delete, and query court schedules
- **Hearing Slot Allocation**: Allocate, search, and manage hearing slots
- **Rota File Processing**: Process XML rota files from Azure Blob Storage to create court schedules
- **Judiciary Management**: Associate judges and magistrates with court schedules
- **Provisional Bookings**: Support provisional slot bookings
- **Availability Calculation**: Real-time calculation of available slots and duration
- **Migration Support**: Handle migration of operational units (OU codes) between systems

---

## Architecture

### Module Structure
The service is organized as a multi-module Maven project:

1. **listingcourtscheduler-api**: REST API layer with RAML definitions
2. **listingcourtscheduler-domain**: Core domain models and business logic
3. **listingcourtscheduler-common**: Shared utilities and services
4. **listingcourtscheduler-viewstore**: Data persistence layer
   - **listingcourtscheduler-viewstore-persistence**: JPA entities and repositories
   - **listingcourtscheduler-viewstore-liquibase**: Database migrations
   - **listingcourtscheduler-jdbc**: JDBC utilities
5. **listingcourtscheduler-rota-file-processor**: Rota file processing engine
6. **listingcourtscheduler-cache**: Caching layer (Redis)
7. **listingcourtscheduler-healthchecks**: Health check endpoints
8. **listingcourtscheduler-integration-test**: Integration tests

### Technology Stack
- **Framework**: Java EE / Jakarta EE
- **Build Tool**: Maven
- **Database**: PostgreSQL (via JPA/Hibernate)
- **Caching**: Redis (Lettuce client)
- **Cloud Storage**: Azure Blob Storage
- **API Documentation**: RAML 0.8
- **Messaging**: JSON Envelope pattern (Justice Services framework)

---

## Core Functionality

### 1. Court Schedule Management

#### Creating Court Schedules
Court schedules can be created with the following characteristics:
- **Session Types**: AM (morning), PM (afternoon), or AD (all-day)
- **Panel Types**: ADULT or YOUTH
- **Business Types**: Slot-based or duration-based
- **Repeat Patterns**: ONCE or EVERY_WEEK with configurable frequency
- **All-Day Split**: Support for split morning/afternoon durations

**Process Flow:**
1. Validate request parameters (court room, business type, dates, etc.)
2. Check for duplicate sessions
3. Enrich with reference data (court room details, business type properties)
4. Generate schedules based on repeat pattern
5. Calculate availability (slots or duration)
6. Persist to database

#### Updating Court Schedules
Updates are subject to strict validation:
- Cannot change business type from slot-based to duration-based (or vice versa)
- Cannot modify session times if hearings are already booked
- Cannot change all-day split flag if already set
- Session start time must be before end time
- AM sessions cannot end after 13:00
- PM sessions cannot start before 14:00

#### Deleting Court Schedules
- Only schedules without allocated hearings can be deleted
- Returns list of schedules that could not be deleted (with reasons)

### 2. Hearing Slot Management

#### Slot Allocation
The system supports two types of allocations:

**Slot-Based:**
- Each hearing consumes one slot
- Available slots = Max slots - Total allocated slots
- Used for business types configured as slot-based

**Duration-Based:**
- Each hearing consumes duration in minutes
- Available duration = Max duration - Sum of allocated durations
- Supports all-day split with separate morning/afternoon tracking
- Used for business types configured as duration-based

#### Availability Calculation
Real-time availability is calculated using SQL aggregations:
```sql
-- Slot-based
available_slot = max_slot - count(allocated_listings.duration)

-- Duration-based
available_duration_mins = max_duration_mins - sum(allocated_listings.duration)

-- All-day split (morning/afternoon)
totalbookedformorning = SUM of durations before national_break_time
totalbookedforafternoon = SUM of durations after national_break_time
```

#### Overbooking
- Configurable per court schedule via `isOverbookingAllowed` flag
- Allows allocation beyond available capacity
- Tracked separately to identify overbooked slots

### 3. Search and Query Capabilities

#### Hearing Slot Search
Supports complex filtering:
- Panel type (ADULT/YOUTH)
- Date range (sessionStartDate, sessionEndDate)
- Court centre, court room
- Business type
- Court session (AM/PM/AD)
- Slot-based vs duration-based
- Exact hearing start time matching
- Operational unit (OU code) filtering

#### Court Schedule Query
- Filter by court centre, court room, business type
- Date range filtering
- Pagination support
- Returns enriched data with business descriptions

---

## Business Rules

### Session Type Rules

1. **AM Session:**
   - End time cannot exceed 13:00
   - Start time cannot be earlier than 01:00

2. **PM Session:**
   - Start time cannot be earlier than 14:00
   - End time cannot be later than 23:59

3. **All-Day (AD) Session:**
   - Start time cannot be earlier than 01:00
   - End time cannot be later than 23:59
   - Can be split into morning/afternoon portions

### Panel Type Rules

1. **ADULT and YOUTH panels are mutually exclusive:**
   - Cannot have both ADULT and YOUTH panels for the same:
     - OU code
     - Business type
     - Session date
     - Court room number
   - If ADULT panel exists, YOUTH panel will not be persisted
   - If YOUTH panel exists, ADULT panel will not be persisted

### Session Type Conflict Rules

1. **All-Day vs AM/PM:**
   - Cannot have both AD session and AM/PM sessions for the same:
     - OU code
     - Business type
     - Session date
     - Court room number
   - If AD session exists, AM/PM sessions will not be persisted
   - If AM/PM sessions exist, AD session will not be persisted

### Business Type Rules

1. **Slot vs Duration:**
   - Business type determines if schedule is slot-based or duration-based
   - Cannot change from slot-based to duration-based (or vice versa) during update
   - Slot-based: Uses `maxSlots` and `availableSlots`
   - Duration-based: Uses `maxDuration` and `availableDuration`

2. **All-Day Split:**
   - Only applicable to duration-based, all-day sessions
   - Requires `supportAdSplit = true`
   - Tracks separate morning and afternoon durations
   - National break time used as split point

### Update Restrictions

1. **Cannot modify if hearings booked:**
   - Court room ID
   - Session type (AM/PM/AD)
   - Panel type (ADULT/YOUTH)

2. **Time constraints:**
   - Session start time cannot be changed to after earliest hearing start time
   - Session end time cannot be changed to before latest hearing start time

3. **All-day split:**
   - Cannot change `allDaySplit` flag if already set

### Duplicate Session Prevention

- Validates against existing sessions before creation
- Checks for conflicts based on:
  - Court centre ID
  - Court room ID
  - Business type
  - Session date
  - Session type (AM/PM/AD)
  - Panel type (ADULT/YOUTH)

### Provisional Data Population

- Rota files can populate provisional data for future periods
- Configurable via properties:
  - `rota.months.of.provisional.data.to.populate` (default: 6 months)
  - `rota.cycle.to.populate.length` (default: 28 days)

---

## API Endpoints

### Court Schedule Endpoints

#### POST `/courtschedule`
**Purpose:** Create court schedules

**Request:** `application/vnd.courtscheduler.create+json`

**Key Fields:**
- `sessionList`: Array of session definitions
- `repeatPattern`: Frequency (ONCE/EVERY_WEEK), start/end dates
- Each session includes: courtCentreId, courtRoomId, businessType, panel, sessionType, slotsOrDuration

**Response:** 202 Accepted

**Business Logic:**
- Validates all input parameters
- Checks for duplicate sessions
- Enriches with reference data
- Generates schedules based on repeat pattern
- Calculates initial availability

#### GET `/courtschedule`
**Purpose:** Query court schedules

**Query Parameters:**
- `courtCentreId` (required)
- `courtRoomId` (optional)
- `businessType` (optional)
- `sessionStartDate` (required)
- `sessionEndDate` (required)
- `pageSize` (required)
- `pageNumber` (required)

**Response:** `application/vnd.courtscheduler.get+json`

**Returns:** Paginated list of court schedules with availability information

#### POST `/courtschedule/edit`
**Purpose:** Update existing court schedule

**Request:** `application/vnd.courtscheduler.update+json`

**Key Fields:**
- `courtScheduleId` (required)
- `courtRoomId`, `courtSession`, `businessType`, `panel` (required)
- `maxSlots` OR `maxDuration` (depending on business type)
- `sessionStartTime`, `sessionEndTime` (optional)
- `isOverbookingAllowed` (optional)

**Response:** 202 Accepted

**Business Logic:**
- Validates update is allowed (no conflicting bookings)
- Recalculates availability
- Updates session times with constraints

#### POST `/courtschedule/delete`
**Purpose:** Delete court schedules

**Request:** `application/vnd.courtscheduler.delete+json`

**Response:** List of schedules that could not be deleted (with reasons)

**Business Logic:**
- Only deletes schedules without allocated hearings
- Returns error details for schedules that cannot be deleted

#### GET `/courtschedule/search.court-schedules-by-id`
**Purpose:** Search court schedules by IDs

**Query Parameters:**
- `courtScheduleIds`: Comma-separated list of UUIDs

**Response:** List of matching court schedules

### Hearing Slot Endpoints

#### PUT `/hearingslots`
**Purpose:** Allocate hearing slots

**Request:** `application/vnd.courtscheduler.update.hearing.slots+json`

**Key Fields:**
- `hearingId`
- `courtScheduleId`
- `hearingStartTime`
- `duration`
- `source` (SPI/Online/API)

**Response:** Updated court schedule with new availability

**Business Logic:**
- Validates slot/duration availability
- Creates allocated listing record
- Updates court schedule availability
- Handles overbooking if allowed

#### GET `/hearingslots`
**Purpose:** Search available hearing slots

**Query Parameters:**
- `panel` (required): ADULT/YOUTH/ADULT,YOUTH
- `sessionStartDate`, `sessionEndDate` (required)
- `exactHearingStartDateTime` (optional): Exact time match
- `oucodeL2Code`, `ouCode` (optional): Operational unit filters
- `courtRoomId`, `courtRoomNumber` (optional)
- `businessType` (optional)
- `courtSession` (optional): AM/PM/AD
- `isSlotBased` (optional)
- `hearingStartTime`, `duration` (optional)
- `pageSize`, `pageNumber` (required)
- `showOverbookedSlots` (optional): Include overbooked slots
- `caseIdentifier` (optional): Source channel

**Response:** `application/vnd.courtscheduler.get.hearing.slots+json`

**Returns:** Paginated list of available hearing slots matching criteria

#### DELETE `/hearingslots/{hearingId}`
**Purpose:** Remove hearing slots for a hearing

**Request:** `application/vnd.courtscheduler.remove.hearing.slots+json`

**Response:** 202 Accepted

**Business Logic:**
- Removes all allocated listings for the hearing
- Recalculates availability for affected court schedules

#### PUT `/list/hearingslots`
**Purpose:** Allocate multiple hearing slots in court sessions

**Request:** `application/vnd.courtscheduler.list.hearings-in-court-sessions+json`

**Response:** List of allocated hearings with status

#### PUT `/searchupdate/hearingslots`
**Purpose:** Search and allocate hearing slot when courtScheduleId is null

**Request:** `application/vnd.courtscheduler.search.update.hearing.slots+json`

**Response:** 204 No Content

#### GET `/searchlist/hearingslots`
**Purpose:** Search and list hearings in court sessions

**Query Parameters:**
- `hearingId` (required)
- `courtCentreId` (required)
- `hearingDate` (required)
- `durationInMinutes` (required)
- `courtRoomId`, `hearingStartTime` (optional)
- `hearingSessionDateSearchCutOff` (optional)
- `isPolice` (optional)

**Response:** `application/vnd.courtscheduler.search.book.hearing.slots+json`

#### GET `/hearingslots` (Hearing IDs)
**Purpose:** Get hearing IDs for allocated slots

**Response:** `application/vnd.courtscheduler.get.hearing.ids+json`

### Provisional Booking Endpoints

#### POST `/provisionalBooking`
**Purpose:** Create provisional booking

**Request:** `application/vnd.courtscheduler.create.provisional.booking+json`

**Key Fields:**
- `courtScheduleIds`: Array of court schedule IDs to book provisionally

**Response:** Provisional booking details with booking IDs

#### GET `/provisionalBooking`
**Purpose:** Get provisional booking details

**Query Parameters:**
- `bookingIds`: Comma-separated booking IDs

**Response:** `application/vnd.courtscheduler.get.provisional.booking+json`

### Management Information (MI) Endpoints

#### GET `/mi/court_schedules`
**Purpose:** Export court schedules for reporting

**Query Parameters:**
- `fromDate`, `toDate` (required)

**Response:** `application/vnd.courtscheduler.export.court_schedule+json`

#### GET `/mi/court_schedule_judiciaries`
**Purpose:** Export court schedule judiciary assignments

**Query Parameters:**
- `fromDate`, `toDate` (required)

**Response:** `application/vnd.courtscheduler.export.court_schedule_judiciary+json`

#### GET `/mi/allocated_listings`
**Purpose:** Export allocated listings

**Query Parameters:**
- `fromDate`, `toDate` (required)

**Response:** `application/vnd.courtscheduler.export.allocated_listings+json`

### Operational Endpoints

#### POST `/oucode/recalculate-availability`
**Purpose:** Recalculate availability for an OU code

**Request:** `application/vnd.courtscheduler.oucode.recalculate.availability+json`

**Business Logic:**
- Recalculates availability for all court schedules for the OU code
- Fixes inconsistencies in availability calculations

#### POST `/oucode/migrate`
**Purpose:** Mark OU codes as migrated/non-migrated

**Request:** `application/vnd.courtscheduler.oucode.migrate+json`

**Key Fields:**
- `ouCodes`: Array of OU codes
- `migrated`: Boolean flag

**Business Logic:**
- Updates migration status for OU codes
- Affects how rota files are processed (migrated vs non-migrated)

### Rota File Processing Endpoints

#### POST `/rotasl/process-rota-files`
**Purpose:** Trigger processing of rota files from Azure Blob Storage

**Request:** `application/vnd.courtscheduler.rotasl.process_rota_files+json`

**Key Fields:**
- `forItTest` (optional): Boolean flag for integration test mode

**Response:** 202 Accepted

**Business Logic:**
- Asynchronously processes rota files from Azure Blob Storage
- Files are processed, archived, and deleted from input container
- Supports both full rota files and snapshot files

#### POST `/rotasl/clean-redundant-rota-data`
**Purpose:** Clean up old rota data

**Request:** `application/vnd.courtscheduler.rotasl.clean_redundant_rota_data+json`

**Response:** 202 Accepted

**Business Logic:**
- Deletes court schedules older than configured retention period

### Validation Endpoints

#### POST `/validate`
**Purpose:** Validate requests before actual execution

**Supported Request Types:**
- `application/vnd.courtscheduler.validate.create+json`
- `application/vnd.courtscheduler.validate.update+json`
- `application/vnd.courtscheduler.validate.delete+json`

**Response:** Validation results (200 OK) or errors (400 Bad Request)

#### POST `/validate-session-availability`
**Purpose:** Validate session availability

**Request:** `application/vnd.courtscheduler.validate.session.availability+json`

**Response:** Validation results

## Rota File Processing

### Overview
The rota file processing system ingests XML files from Azure Blob Storage containing court scheduling information from the Legal Aid Agency (LJA) system. These files are parsed, enriched with reference data, and converted into court schedules.

### File Types

1. **Full Rota Files:**
   - Prefix: `lja_`
   - Contains complete rota information for a period
   - Replaces existing schedules for the period

2. **Snapshot Rota Files:**
   - Contains `_snapshot_` in filename
   - Contains timestamp in filename
   - Used for incremental updates
   - Only processes if no newer snapshot exists

3. **Dummy Support Files:**
   - Contains `dummysupport` in filename
   - Skipped during processing

### Processing Flow

#### 1. File Capture
- `RotaFileCaptureAndProcessTriggerService` monitors Azure Blob Storage
- Acquires lease on blob files to prevent concurrent processing
- Downloads files for processing

#### 2. File Parsing
- `RotaFileParser` parses XML content
- Extracts:
  - Location data
  - Court room information
  - Business type assignments
  - Session allocations
  - Judiciary assignments
  - Date ranges

#### 3. Data Enrichment

**Location to OU Code Mapping:**
- Maps rota location IDs to operational unit codes (OU codes)
- Uses reference data service to resolve court room mappings

**Rota Period Detection:**
- Determines start and end dates of rota period
- Calculates number of months covered

**Business Type Resolution:**
- Loads business type map from reference data
- Determines slot-based vs duration-based configuration

**Migration Status Check:**
- Checks if OU codes are migrated or non-migrated
- Processes migrated and non-migrated OU codes separately

#### 4. Slot Generation

**For Non-Migrated OU Codes:**
- Extracts existing court schedules for the rota period
- Enriches rota data to create new slots
- Compares with existing schedules to determine:
  - New slots to create
  - Existing slots to update
  - Slots to delete (no longer in rota)

**For Migrated OU Codes:**
- Similar process but tracks separately
- Maintains separate judiciary schedules

#### 5. Judiciary Schedule Enrichment
- `JudiciaryScheduleEnricher` associates judges with court schedules
- Extracts judiciary assignments from rota file
- Creates `CourtScheduleJudiciary` records with positions

#### 6. Persistence

**Snapshot File Processing:**
- Processes in weekly chunks
- Maintains processing history
- Tracks execution ID for audit

**Full Rota File Processing:**
- Processes in weekly chunks
- Updates or creates schedules
- Deletes obsolete schedules

**Business Rules Applied:**
- Panel type conflicts (ADULT vs YOUTH)
- Session type conflicts (AD vs AM/PM)
- Only persists slots that pass conflict checks

#### 7. Provisional Data Population
- For future rota periods, creates provisional schedules
- Configurable via `rota.months.of.provisional.data.to.populate`
- Uses `rota.cycle.to.populate.length` for cycle length

#### 8. File Archival
- Uploads processed files to archive container
- Releases blob lease
- Deletes file from input container

### Processing Logic

#### Slot Persistence Decision
A slot is persisted only if:
1. No conflicting ADULT/YOUTH panel exists
2. No conflicting AD/AM-PM session exists
3. Valid business type mapping exists
4. Valid court room mapping exists

#### Availability Calculation
- For slot-based: `availableSlots = maxSlots - count(allocated_listings)`
- For duration-based: `availableDuration = maxDuration - sum(allocated_listings.duration)`
- For all-day split: Separate morning/afternoon calculations

#### Update Strategy
- Compares new slots with existing slots
- Identifies:
  - New slots (to insert)
  - Updated slots (to update)
  - Deleted slots (to mark inactive or delete)
- Updates judiciary schedules with new positions

### Error Handling

- **File Processing Errors:**
  - Releases blob lease on error
  - Logs error details
  - Continues with next file

- **Validation Errors:**
  - Skips invalid records
  - Logs warnings
  - Continues processing

- **Database Errors:**
  - Transaction rollback on critical errors
  - Retry logic for transient errors

### Performance Optimizations

- **Batch Processing:**
  - Processes files in weekly chunks
  - Reduces memory footprint
  - Enables incremental processing

- **Caching:**
  - Caches reference data (business types, court rooms)
  - Reduces external service calls

- **Parallel Processing:**
  - Separate processing for migrated/non-migrated OU codes
  - Asynchronous file processing

---

## Data Models

### Core Domain Models

#### CourtSchedule
Represents a court session with availability information.

**Key Fields:**
- `courtScheduleId`: Unique identifier (UUID)
- `listingProfileId`: Profile ID from rota file
- `ouCode`: Operational unit code
- `courtRoomId`: Court room UUID
- `courtRoomNumber`: Court room number
- `courtHouseId`: Court centre ID
- `businessType`: Business type code
- `panel`: ADULT or YOUTH
- `courtSession`: AM, PM, or AD
- `slotBased`: Boolean indicating slot-based or duration-based
- `active`: Boolean indicating if schedule is active
- `sessionDate`: Date of the session
- `maxSlots`: Maximum slots (slot-based)
- `availableSlots`: Available slots (slot-based)
- `maxDuration`: Maximum duration in minutes (duration-based)
- `availableDuration`: Available duration in minutes (duration-based)
- `allDaySplit`: Boolean for all-day split support
- `maxDurationForMorning`: Maximum morning duration
- `maxDurationForAfternoon`: Maximum afternoon duration
- `totalBooked`: Total booked duration
- `availableDurationForMorning`: Available morning duration
- `availableDurationForAfternoon`: Available afternoon duration
- `totalBookedForMorning`: Total booked morning duration
- `totalBookedForAfternoon`: Total booked afternoon duration
- `isOverbookingAllowed`: Boolean for overbooking
- `sessionStartTime`: Session start time
- `sessionEndTime`: Session end time
- `nationalBreakTime`: National break time (for all-day split)

**Relationships:**
- One-to-many with `CourtScheduleJudiciary`
- One-to-many with `AllocatedListing`
- One-to-many with `SlotStartTime`

#### AllocatedListing
Represents a hearing allocated to a court schedule.

**Key Fields:**
- `id`: Unique identifier (UUID)
- `courtScheduleId`: Reference to court schedule
- `bookingId`: Provisional booking ID (if applicable)
- `hearingId`: Hearing identifier
- `oucode`: Operational unit code
- `courtRoomId`: Court room number
- `rotaBusinessType`: Business type from rota
- `duration`: Duration in minutes
- `hearingStartTime`: Hearing start time
- `source`: Source of allocation (SPI/Online/API)
- `createdOn`: Creation timestamp
- `updatedOn`: Update timestamp

#### CourtScheduleJudiciary
Represents a judge/magistrate assigned to a court schedule.

**Key Fields:**
- `courtScheduleId`: Reference to court schedule
- `judiciaryId`: Judiciary identifier
- `position`: Position/role in the session
- `updatedOn`: Update timestamp

**Composite Key:**
- `courtScheduleId` + `judiciaryId`

#### ProvisionalBooking
Represents a provisional booking of court schedules.

**Key Fields:**
- `bookingId`: Unique booking identifier
- `courtScheduleIds`: Array of court schedule IDs
- `createdOn`: Creation timestamp
- `expiresOn`: Expiration timestamp

#### Session
Domain model for session creation requests.

**Key Fields:**
- `courtCentreId`: Court centre identifier
- `courtRoomId`: Court room identifier
- `sessionType`: AM, PM, or AD
- `businessType`: Business type code
- `slotsOrDuration`: Maximum slots or duration
- `panel`: ADULT or YOUTH
- `repeatDays`: Set of days of week
- `allDaySplit`: Boolean for all-day split
- `maxDurationForMorning`: Maximum morning duration
- `maxDurationForAfternoon`: Maximum afternoon duration
- `sessionStartTime`: Session start time
- `sessionEndTime`: Session end time
- `isOverbookingAllowed`: Boolean for overbooking

#### BusinessType
Represents a business type configuration.

**Key Fields:**
- `id`: Unique identifier
- `typeCode`: Business type code
- `typeDescription`: Description
- `slot`: Boolean indicating slot-based
- `duration`: Boolean indicating duration-based

#### Judiciary
Represents a judge or magistrate.

**Key Fields:**
- `id`: Unique identifier
- `surname`, `forenames`: Name
- `judiciaryType`: Type of judiciary
- `emailAddress`: Email
- `personId`: Person identifier
- `validFrom`, `validTo`: Validity period

### Request/Response Models

#### CreateSessionRequestParam
Request model for creating sessions.

**Key Fields:**
- `sessionList`: Array of Session objects
- `repeatPattern`: RepeatPattern object
  - `frequency`: ONCE or EVERY_WEEK
  - `startDate`: Start date
  - `endDate`: End date (required for EVERY_WEEK)
  - `repeatFor`: Number of weeks between repeats

#### UpdateCourtSchedule
Request model for updating court schedules.

**Key Fields:**
- `courtScheduleId`: Court schedule ID
- `courtRoomId`: Court room ID
- `courtSession`: AM, PM, or AD
- `businessType`: Business type code
- `panel`: ADULT or YOUTH
- `maxSlots`: Maximum slots (if slot-based)
- `maxDuration`: Maximum duration (if duration-based)
- `maxDurationForMorning`: Maximum morning duration
- `maxDurationForAfternoon`: Maximum afternoon duration
- `allDaySplit`: Boolean for all-day split
- `sessionStartTime`: Session start time
- `sessionEndTime`: Session end time
- `isOverbookingAllowed`: Boolean for overbooking

#### HearingSlotRequestParam
Request model for searching hearing slots.

**Key Fields:**
- `panel`: Panel type filter
- `sessionStartDate`, `sessionEndDate`: Date range
- `exactHearingStartDateTime`: Exact time match
- `oucodeL2Code`, `ouCode`: Operational unit filters
- `courtRoomId`, `courtRoomNumber`: Court room filters
- `businessType`: Business type filter
- `courtSession`: Session type filter
- `isSlotBased`: Slot-based filter
- `hearingStartTime`, `duration`: Time/duration filters
- `pageSize`, `pageNumber`: Pagination
- `showOverbookedSlots`: Include overbooked slots

#### AllocatedSlot
Represents an allocated hearing slot.

**Key Fields:**
- `hearingId`: Hearing identifier
- `courtScheduleId`: Court schedule ID
- `hearingStartTime`: Hearing start time
- `duration`: Duration in minutes
- `source`: Source of allocation

---

## Technical Implementation

### Transaction Management

- **Stateless EJBs**: Used for stateless operations
- **Transaction Attributes:**
  - `REQUIRED`: Default for most operations
  - `NOT_SUPPORTED`: For file processing to avoid long transactions
- **Transactional Methods**: Database operations wrapped in transactions

### Concurrency Control

- **Blob Leasing**: Azure Blob Storage leases prevent concurrent file processing
- **Optimistic Locking**: JPA version fields for entity updates
- **ConcurrentHashMap**: For in-memory migration status tracking

### Caching Strategy

- **Redis Cache**: For reference data caching
- **Cache Keys**: Business types, court rooms, judiciaries
- **TTL**: Configurable time-to-live (default: 86400 seconds)
- **Fallback**: Direct reference data service calls if cache unavailable

### Error Handling

- **ValidationException**: For validation errors (400 Bad Request)
- **BadRequestException**: For invalid requests
- **RuntimeException**: For unexpected errors (500 Internal Server Error)
- **Error Messages**: Centralized in `ErrorMessages` class

### Logging

- **SLF4J**: Logging framework
- **Log Levels:**
  - `INFO`: Business operations, processing milestones
  - `DEBUG`: Detailed processing information
  - `WARN`: Non-critical issues, skipped records
  - `ERROR`: Errors requiring attention
- **Performance Logging**: Nanosecond timestamps for performance monitoring

### Database Queries

- **JPA Repositories**: DeltaSpike Data for repository pattern
- **Native Queries**: Complex aggregations for availability calculations
- **Query Optimization:**
  - Indexed columns: `session_start`, `oucode`, `court_room_id`, `business_type`
  - Batch processing for large datasets
  - Pagination for result sets

### Availability Calculation Logic

**Slot-Based:**
```sql
available_slot = max_slot - count(allocated_listings.duration)
```

**Duration-Based:**
```sql
available_duration_mins = max_duration_mins - sum(allocated_listings.duration)
```

**All-Day Split (Morning):**
```sql
totalbookedformorning = SUM(
  CASE
    WHEN hearing_start_time < national_break_time 
         AND hearing_start_time + duration <= national_break_time
    THEN duration
    WHEN hearing_start_time < national_break_time 
         AND hearing_start_time + duration > national_break_time
    THEN EXTRACT(EPOCH FROM (national_break_time - hearing_start_time)) / 60
    ELSE 0
  END
)
```

**All-Day Split (Afternoon):**
```sql
totalbookedforafternoon = SUM(
  CASE
    WHEN hearing_start_time >= national_break_time
    THEN duration
    WHEN hearing_start_time < national_break_time 
         AND hearing_start_time + duration > national_break_time
    THEN EXTRACT(EPOCH FROM (hearing_start_time + duration - national_break_time)) / 60
    ELSE 0
  END
)
```

### Reference Data Integration

- **Reference Data Service**: External service for court rooms, business types, judiciaries
- **Caching**: Redis cache to reduce service calls
- **Fallback**: Direct service calls if cache miss
- **Mapping**: Location IDs to OU codes, business type codes to configurations

### Migration Support

- **Migration Status Table**: Tracks OU code migration status
- **Separate Processing**: Migrated and non-migrated OU codes processed separately
- **Migration Endpoint**: API to update migration status
- **Impact**: Affects how rota files are processed and which schedules are updated

---

## Configuration

### Application Properties

#### Rota File Processing
- `rota.months.of.provisional.data.to.populate`: Number of months to populate provisional data (default: 6)
- `rota.cycle.to.populate.length`: Length of rota cycle in days (default: 28)

#### Azure Blob Storage
- Azure storage account connection string
- Container names (input, archive)
- Blob prefix filters (`lja_`, `IT_Test_`)

#### Redis Cache
- `redisCommonCacheHost`: Redis host (default: localhost)
- `redisCommonCachePort`: Redis port (default: 6380)
- `redisCommonCacheKey`: Cache key prefix
- `redisCommonCacheUseSsl`: Use SSL (default: false)
- `redisCommonCacheKeyTTL`: Time-to-live in seconds (default: 86400)

#### Database
- JPA persistence configuration
- Connection pool settings
- Transaction timeout

### Environment-Specific Configuration

- **Development**: Local database, mock Azure storage
- **Integration Test**: H2 in-memory database, test data
- **Production**: PostgreSQL, Azure Blob Storage, Redis cluster

### Feature Flags

- Migration status per OU code
- HMI listing/scheduling/pubhub flags per organisation unit

---

## Integration Points

### External Services

#### Reference Data Service
- **Purpose**: Provides court rooms, business types, judiciaries
- **Protocol**: REST API
- **Caching**: Redis cache to reduce calls
- **Endpoints Used:**
  - Court room mappings
  - Business type definitions
  - Judiciary information
  - Organisation unit details

#### Users & Groups Service
- **Purpose**: User permissions and authentication
- **Protocol**: REST API
- **Usage**: Access control for API endpoints

### Azure Services

#### Azure Blob Storage
- **Input Container**: Receives rota files from LJA system
- **Archive Container**: Stores processed files
- **Blob Leasing**: Prevents concurrent processing
- **Operations:**
  - List blobs with prefix
  - Acquire lease
  - Download blob
  - Upload to archive
  - Delete from input

### Database

#### PostgreSQL
- **Schema**: Managed via Liquibase migrations
- **Tables:**
  - `court_schedule`: Court schedules
  - `allocated_listings`: Allocated hearings
  - `court_schedule_judiciary`: Judiciary assignments
  - `provisional_booking`: Provisional bookings
  - `court_scheduler_migration_status`: Migration status
  - `rota_file_process_history`: Processing history

### Messaging

#### JSON Envelope Pattern
- **Framework**: Justice Services framework
- **Message Types**: Request/response envelopes
- **Metadata**: Includes action names, user context

---

## Additional Notes

### Performance Considerations

1. **Batch Processing**: Rota files processed in weekly chunks
2. **Caching**: Reference data cached to reduce external calls
3. **Pagination**: All list endpoints support pagination
4. **Indexing**: Database indexes on frequently queried columns
5. **Async Processing**: File processing runs asynchronously

### Security

- **Access Control**: Integration with Users & Groups service
- **Input Validation**: All inputs validated before processing
- **SQL Injection Prevention**: Parameterized queries via JPA
- **Azure Security**: Managed identity for blob storage access

### Monitoring

- **Health Checks**: Dedicated health check endpoints
- **Logging**: Comprehensive logging for troubleshooting
- **Performance Metrics**: Nanosecond timestamps for performance monitoring
- **Error Tracking**: Structured error messages and exceptions

### Testing

- **Unit Tests**: Domain logic and utilities
- **Integration Tests**: Full API and database integration
- **Test Data**: H2 database with seeded reference data
- **Mock Services**: Stubbed external services for testing

---

## Conclusion

The Court Scheduler Service is a comprehensive scheduling system that manages court schedules, hearing allocations, and rota file processing. It enforces strict business rules, provides flexible querying capabilities, and integrates with multiple external systems to provide a complete court scheduling solution.

For additional information, refer to:
- RAML API documentation: `listingcourtscheduler-api/src/raml/courtscheduler-api.raml`
- Database schema: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-liquibase`
- Integration tests: `listingcourtscheduler-integration-test`
