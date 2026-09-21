import {
  DataTypes,
  Model,
  type CreationOptional,
  type ForeignKey,
  type InferAttributes,
  type InferCreationAttributes,
  type Sequelize,
} from 'sequelize';
import { getSequelize } from '../config/database';

const connection = getSequelize();
if (!connection) {
  throw new Error('Database is not configured: set DB_NAME, DB_USER and DB_PASSWORD in server/.env');
}
export const sequelize: Sequelize = connection;

export type UserStatus = 'active' | 'suspended' | 'deleted';

export class User extends Model<InferAttributes<User>, InferCreationAttributes<User>> {
  declare id: CreationOptional<number>;
  declare publicId: CreationOptional<string>;
  declare firebaseUid: string;
  declare phone: CreationOptional<string | null>;
  declare email: CreationOptional<string | null>;
  declare emailVerified: CreationOptional<boolean>;
  declare signInProvider: CreationOptional<string | null>;
  declare status: CreationOptional<UserStatus>;
  declare termsVersion: CreationOptional<string | null>;
  declare termsAcceptedAt: CreationOptional<Date | null>;
  declare profileCompletedAt: CreationOptional<Date | null>;
  declare lastLoginAt: CreationOptional<Date | null>;
  declare displayName: CreationOptional<string | null>;
  declare username: CreationOptional<string | null>;
  /** 'YYYY-MM-DD'. Private. */
  declare birthDate: CreationOptional<string | null>;
  declare countryCode: CreationOptional<string | null>;
  declare avatarKey: CreationOptional<string | null>;
  declare ageCheckFailedAt: CreationOptional<Date | null>;
  declare createdAt: CreationOptional<Date>;
  declare updatedAt: CreationOptional<Date>;
}

User.init(
  {
    id: { type: DataTypes.BIGINT.UNSIGNED, autoIncrement: true, primaryKey: true },
    publicId: { type: DataTypes.UUID, allowNull: false, unique: true, defaultValue: DataTypes.UUIDV4, field: 'public_id' },
    firebaseUid: { type: DataTypes.STRING(128), allowNull: false, unique: true, field: 'firebase_uid' },
    phone: { type: DataTypes.STRING(20), field: 'phone_e164' },
    email: { type: DataTypes.STRING(255) },
    emailVerified: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false, field: 'email_verified' },
    signInProvider: { type: DataTypes.STRING(40), field: 'sign_in_provider' },
    status: { type: DataTypes.ENUM('active', 'suspended', 'deleted'), allowNull: false, defaultValue: 'active' },
    termsVersion: { type: DataTypes.STRING(20), field: 'terms_version' },
    termsAcceptedAt: { type: DataTypes.DATE(3), field: 'terms_accepted_at' },
    profileCompletedAt: { type: DataTypes.DATE(3), field: 'profile_completed_at' },
    lastLoginAt: { type: DataTypes.DATE(3), field: 'last_login_at' },
    displayName: { type: DataTypes.STRING(30), field: 'display_name' },
    username: { type: DataTypes.STRING(20) },
    birthDate: { type: DataTypes.DATEONLY, field: 'birth_date' },
    countryCode: { type: DataTypes.CHAR(2), field: 'country_code' },
    avatarKey: { type: DataTypes.STRING(64), field: 'avatar_key' },
    ageCheckFailedAt: { type: DataTypes.DATE(3), field: 'age_check_failed_at' },
    createdAt: DataTypes.DATE(3),
    updatedAt: DataTypes.DATE(3),
  },
  { sequelize, tableName: 'users' },
);

export class RefreshToken extends Model<InferAttributes<RefreshToken>, InferCreationAttributes<RefreshToken>> {
  declare id: CreationOptional<number>;
  declare userId: ForeignKey<User['id']>;
  declare tokenHash: string;
  declare familyId: string;
  declare expiresAt: Date;
  declare revokedAt: CreationOptional<Date | null>;
  declare ip: CreationOptional<string | null>;
  declare userAgent: CreationOptional<string | null>;
  declare createdAt: CreationOptional<Date>;
}

RefreshToken.init(
  {
    id: { type: DataTypes.BIGINT.UNSIGNED, autoIncrement: true, primaryKey: true },
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: 'user_id' },
    tokenHash: { type: DataTypes.CHAR(64), allowNull: false, unique: true, field: 'token_hash' },
    familyId: { type: DataTypes.CHAR(36), allowNull: false, field: 'family_id' },
    expiresAt: { type: DataTypes.DATE(3), allowNull: false, field: 'expires_at' },
    revokedAt: { type: DataTypes.DATE(3), field: 'revoked_at' },
    ip: { type: DataTypes.STRING(45) },
    userAgent: { type: DataTypes.STRING(255), field: 'user_agent' },
    createdAt: DataTypes.DATE(3),
  },
  { sequelize, tableName: 'refresh_tokens', updatedAt: false },
);

User.hasMany(RefreshToken, { foreignKey: 'userId', onDelete: 'CASCADE' });
RefreshToken.belongsTo(User, { foreignKey: 'userId' });
