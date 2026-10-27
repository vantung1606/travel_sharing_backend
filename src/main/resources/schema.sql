-- =============================================================================
-- WAYFARE SMART TRAVEL PLATFORM - MYSQL DATABASE SCHEMA (28 TABLES)
-- Generated & Verified directly from Live MySQL 8.0 Database (wayfare_db)
-- =============================================================================

CREATE DATABASE IF NOT EXISTS wayfare_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wayfare_db;

SET FOREIGN_KEY_CHECKS = 0;
CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `metadata` text,
  `sender_type` enum('USER','ASSISTANT','SYSTEM') NOT NULL,
  `session_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKncfaarj6gwd37j1fi98bmpmcl` (`session_id`),
  CONSTRAINT `FKncfaarj6gwd37j1fi98bmpmcl` FOREIGN KEY (`session_id`) REFERENCES `ai_chat_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `ai_chat_sessions` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `title` varchar(150) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6mxk23rx514hrkne46l8bblp4` (`user_id`),
  CONSTRAINT `FK6mxk23rx514hrkne46l8bblp4` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `chat_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `joined_at` datetime(6) NOT NULL,
  `last_read_message_id` bigint DEFAULT NULL,
  `role` varchar(20) DEFAULT NULL,
  `room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1t5ptackv8b3jsryha1ykq8m5` (`room_id`,`user_id`),
  KEY `FKe0a4ocat8hcxc20kt2m95qejk` (`user_id`),
  CONSTRAINT `FKe0a4ocat8hcxc20kt2m95qejk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKoww5qci3v6n6dxqmbwte0rdbn` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `message_type` varchar(30) NOT NULL,
  `room_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhalwepod3944695ji0suwoqb9` (`room_id`),
  KEY `FKgiqeap8ays4lf684x7m0r2729` (`sender_id`),
  CONSTRAINT `FKgiqeap8ays4lf684x7m0r2729` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKhalwepod3944695ji0suwoqb9` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `chat_rooms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `type` varchar(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `creator_id` bigint DEFAULT NULL,
  `itinerary_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkcyi40l5yas17t11xai5i3m1j` (`creator_id`),
  KEY `FKepldtn3n5f2pbs5mxilurdh26` (`itinerary_id`),
  CONSTRAINT `FKepldtn3n5f2pbs5mxilurdh26` FOREIGN KEY (`itinerary_id`) REFERENCES `itineraries` (`id`),
  CONSTRAINT `FKkcyi40l5yas17t11xai5i3m1j` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `itineraries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `budget_total` decimal(12,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `destination` varchar(150) NOT NULL,
  `end_date` date DEFAULT NULL,
  `is_ai_generated` bit(1) NOT NULL,
  `start_date` date DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `creator_id` bigint NOT NULL,
  `cover_image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK22u1877eptt9hws2jvsj3poel` (`creator_id`),
  CONSTRAINT `FK22u1877eptt9hws2jvsj3poel` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `itinerary_details` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day_number` int NOT NULL,
  `estimated_cost` decimal(12,2) DEFAULT NULL,
  `location_name` varchar(150) DEFAULT NULL,
  `note` text,
  `start_time` time(6) DEFAULT NULL,
  `visit_order` int NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `place_id` bigint DEFAULT NULL,
  `ai_tip` text,
  `category` varchar(50) DEFAULT NULL,
  `location_address` varchar(255) DEFAULT NULL,
  `transit_info` varchar(150) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrosyywx2tyrnd9cwg2j0wlf90` (`itinerary_id`),
  KEY `FKpyppkl2ns1av6dxn1jj64sp6v` (`place_id`),
  CONSTRAINT `FKpyppkl2ns1av6dxn1jj64sp6v` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`),
  CONSTRAINT `FKrosyywx2tyrnd9cwg2j0wlf90` FOREIGN KEY (`itinerary_id`) REFERENCES `itineraries` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `itinerary_expenses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(12,2) NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `receipt_image_url` varchar(500) DEFAULT NULL,
  `itinerary_id` bigint NOT NULL,
  `payer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqoysqu5u1lb6wxb22k4qdef7y` (`itinerary_id`),
  KEY `FKch2t8tk6j0ihwtxf7menp68d8` (`payer_id`),
  CONSTRAINT `FKch2t8tk6j0ihwtxf7menp68d8` FOREIGN KEY (`payer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqoysqu5u1lb6wxb22k4qdef7y` FOREIGN KEY (`itinerary_id`) REFERENCES `itineraries` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `itinerary_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `joined_at` datetime(6) NOT NULL,
  `role` varchar(30) NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrdu1xfi4qlhbfah6552b7jlqn` (`itinerary_id`,`user_id`),
  KEY `FK5q4308hkdcxuxp3x23n7j4ykk` (`user_id`),
  CONSTRAINT `FK5q4308hkdcxuxp3x23n7j4ykk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKbg9uodvs7ib9anf8tgg7nm9kk` FOREIGN KEY (`itinerary_id`) REFERENCES `itineraries` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `message` text NOT NULL,
  `target_url` varchar(500) DEFAULT NULL,
  `type` varchar(50) NOT NULL,
  `actor_id` bigint DEFAULT NULL,
  `recipient_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4sd9fik0uthbk6d9rsxco4uja` (`actor_id`),
  KEY `FKqqnsjxlwleyjbxlmm213jaj3f` (`recipient_id`),
  CONSTRAINT `FK4sd9fik0uthbk6d9rsxco4uja` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqqnsjxlwleyjbxlmm213jaj3f` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `place_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caption` varchar(255) DEFAULT NULL,
  `image_url` varchar(500) NOT NULL,
  `is_primary` bit(1) DEFAULT NULL,
  `place_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfaf8mjrs1svk7fgqxufaajmiq` (`place_id`),
  CONSTRAINT `FKfaf8mjrs1svk7fgqxufaajmiq` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `places` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `average_rating` decimal(3,2) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `cover_image_url` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `latitude` decimal(10,7) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `review_count` int DEFAULT NULL,
  `ticket_price` decimal(12,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `phone_number` varchar(50) DEFAULT NULL,
  `open_hours` varchar(100) DEFAULT NULL,
  `price_range` varchar(100) DEFAULT NULL,
  `category_name` varchar(100) DEFAULT NULL,
  `amenities` varchar(500) DEFAULT NULL,
  `status` varchar(30) DEFAULT 'ACTIVE',
  `is_verified_host` tinyint(1) DEFAULT '0',
  `ai_moderation_status` varchar(50) DEFAULT 'APPROVED_BY_AI',
  `ai_moderation_note` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_places_owner` (`owner_id`),
  CONSTRAINT `fk_places_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `post_bookmarks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post_bookmark` (`user_id`,`post_id`),
  KEY `FKclpw1l6wrci96rfj0dtt3bfah` (`post_id`),
  CONSTRAINT `FK9b5c09u5arho7ei76d78bn7ww` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKclpw1l6wrci96rfj0dtt3bfah` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `post_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `reply_to_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsnxoecngu89u3fh4wdrgf0f2g` (`user_id`),
  KEY `FKaawaqxjs3br8dw5v90w7uu514` (`post_id`),
  KEY `FKc3b7s6wypcsvua2ycn4o1lv2c` (`parent_id`),
  KEY `FKhndchwuclp9m68p2jo1jvm40w` (`reply_to_user_id`),
  CONSTRAINT `FKaawaqxjs3br8dw5v90w7uu514` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
  CONSTRAINT `FKc3b7s6wypcsvua2ycn4o1lv2c` FOREIGN KEY (`parent_id`) REFERENCES `post_comments` (`id`),
  CONSTRAINT `FKhndchwuclp9m68p2jo1jvm40w` FOREIGN KEY (`reply_to_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsnxoecngu89u3fh4wdrgf0f2g` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `post_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5l2rj28vw5oj6f7ox746grokg` (`user_id`,`post_id`),
  KEY `FKa5wxsgl4doibhbed9gm7ikie2` (`post_id`),
  CONSTRAINT `FKa5wxsgl4doibhbed9gm7ikie2` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
  CONSTRAINT `FKkgau5n0nlewg6o9lr4yibqgxj` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(50) NOT NULL,
  `target_id` bigint NOT NULL,
  `target_title` varchar(255) DEFAULT NULL,
  `reporter_id` bigint NOT NULL,
  `category` varchar(100) NOT NULL,
  `reason` varchar(255) NOT NULL,
  `description` text,
  `evidence_image_url` varchar(500) DEFAULT NULL,
  `status` varchar(50) DEFAULT 'PENDING',
  `moderator_notes` text,
  `action_taken` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reports_target` (`target_type`, `target_id`),
  KEY `idx_reports_status` (`status`),
  KEY `FK_reports_reporter` (`reporter_id`),
  CONSTRAINT `FK_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_count` int DEFAULT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `like_count` int DEFAULT NULL,
  `location_tag` varchar(150) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `ai_flag_reason` text,
  `ai_safety_score` int DEFAULT NULL,
  `badge_text` varchar(100) DEFAULT NULL,
  `category` varchar(100) DEFAULT NULL,
  `report_reason` text,
  `reports_count` int DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `itinerary_id` bigint DEFAULT NULL,
  `images` text,
  `video_url` varchar(500) DEFAULT NULL,
  `visibility` varchar(20) NOT NULL DEFAULT 'PUBLIC',
  `shared_post_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5lidm6cqbc7u4xhqpxm898qme` (`user_id`),
  KEY `FKa8uaqytwgfur4636imtq6dref` (`itinerary_id`),
  KEY `FKmk5q3yvwj8drh5btwuvgbc0p0` (`shared_post_id`),
  CONSTRAINT `FK5lidm6cqbc7u4xhqpxm898qme` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKa8uaqytwgfur4636imtq6dref` FOREIGN KEY (`itinerary_id`) REFERENCES `itineraries` (`id`),
  CONSTRAINT `FKmk5q3yvwj8drh5btwuvgbc0p0` FOREIGN KEY (`shared_post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expiry_date` datetime(6) NOT NULL,
  `is_revoked` bit(1) NOT NULL,
  `token` varchar(500) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ghpmfn23vmxfu3spu3lfg4r2d` (`token`),
  KEY `FK1lih5y2npsf8u5o3vhdb9y0os` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` text,
  `created_at` datetime(6) NOT NULL,
  `rating` int NOT NULL,
  `user_id` bigint NOT NULL,
  `place_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcgy7qjc1r99dp117y9en6lxye` (`user_id`),
  KEY `FKcnnkvk0h9vrih5xqxef7sv2r6` (`place_id`),
  CONSTRAINT `FKcgy7qjc1r99dp117y9en6lxye` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKcnnkvk0h9vrih5xqxef7sv2r6` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ofx66keruapi6vyqpv6f2or37` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `saved_places` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `place_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf18edjgxairhnwg69kfyqipul` (`user_id`,`place_id`),
  KEY `FK5u1we43jjspnl9i5plhie618y` (`place_id`),
  CONSTRAINT `FK5u1we43jjspnl9i5plhie618y` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`),
  CONSTRAINT `FKa538njw543jshdhxbi05cbqe7` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `system_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_pk5mof051xp5r3e75s2e23s8s` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `user_follows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `follower_id` bigint NOT NULL,
  `following_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_following` (`follower_id`,`following_id`),
  UNIQUE KEY `UKac5tvpn8oynln4njypmqycil3` (`follower_id`,`following_id`),
  KEY `fk_follow_following` (`following_id`),
  CONSTRAINT `fk_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follow_following` FOREIGN KEY (`following_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKh8ciramu9cc9q3qcqiv4ue8a6` (`role_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar_url` varchar(500) DEFAULT NULL,
  `bio` varchar(500) DEFAULT NULL,
  `budget_preference` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(100) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `handle` varchar(50) DEFAULT NULL,
  `is_verified` bit(1) NOT NULL,
  `password` varchar(255) NOT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `travel_style` varchar(100) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `is_locked` bit(1) NOT NULL,
  `status` varchar(20) NOT NULL,
  `last_login_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_tpu0euqmn8f2vw66lyuv3dfoh` (`handle`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- END OF SCHEMA
-- =============================================================================
