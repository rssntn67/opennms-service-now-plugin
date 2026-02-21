package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessPoint {

    @JsonProperty("Model_ID")
    private String modelId;

    @JsonProperty("u_categoria")
    private String categoria;

    @JsonProperty("sys_class_name")
    private String sysClassName;

    @JsonProperty("asset_tag")
    private String assetTag;

    @JsonProperty("name")
    private String name;

    @JsonProperty("u_marca")
    private String marca;

    @JsonProperty("u_modello")
    private String modello;

    @JsonProperty("location")
    private String location;

    @JsonProperty("u_latitudine")
    private String latitudine;

    @JsonProperty("u_longitudine")
    private String longitudine;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("u_parental_node")
    private String parentalNode;

    @JsonProperty("install_status")
    private InstallStatus installStatus;

    @JsonProperty("u_tipo_collegamento")
    private TipoCollegamento tipoCollegamento;

    @JsonProperty("serial_number")
    private String serialNumber;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getSysClassName() {
        return sysClassName;
    }

    public void setSysClassName(String sysClassName) {
        this.sysClassName = sysClassName;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModello() {
        return modello;
    }

    public void setModello(String modello) {
        this.modello = modello;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLatitudine() {
        return latitudine;
    }

    public void setLatitudine(String latitudine) {
        this.latitudine = latitudine;
    }

    public String getLongitudine() {
        return longitudine;
    }

    public void setLongitudine(String longitudine) {
        this.longitudine = longitudine;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getParentalNode() {
        return parentalNode;
    }

    public void setParentalNode(String parentalNode) {
        this.parentalNode = parentalNode;
    }

    public InstallStatus getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(InstallStatus installStatus) {
        this.installStatus = installStatus;
    }

    public TipoCollegamento getTipoCollegamento() {
        return tipoCollegamento;
    }

    public void setTipoCollegamento(TipoCollegamento tipoCollegamento) {
        this.tipoCollegamento = tipoCollegamento;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    @Override
    public String toString() {
        return "AccessPoint{" +
                "modelId='" + modelId + '\'' +
                ", categoria='" + categoria + '\'' +
                ", sysClassName='" + sysClassName + '\'' +
                ", assetTag='" + assetTag + '\'' +
                ", name='" + name + '\'' +
                ", marca='" + marca + '\'' +
                ", modello='" + modello + '\'' +
                ", location='" + location + '\'' +
                ", latitudine='" + latitudine + '\'' +
                ", longitudine='" + longitudine + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", parentalNode='" + parentalNode + '\'' +
                ", installStatus=" + installStatus +
                ", tipoCollegamento='" + tipoCollegamento + '\'' +
                ", serialNumber='" + serialNumber + '\'' +
                '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof AccessPoint that)) return false;

        return Objects.equals(modelId, that.modelId) && Objects.equals(categoria, that.categoria) && Objects.equals(sysClassName, that.sysClassName) && Objects.equals(assetTag, that.assetTag) && Objects.equals(name, that.name) && Objects.equals(marca, that.marca) && Objects.equals(modello, that.modello) && Objects.equals(location, that.location) && Objects.equals(latitudine, that.latitudine) && Objects.equals(longitudine, that.longitudine) && Objects.equals(ipAddress, that.ipAddress) && Objects.equals(parentalNode, that.parentalNode) && installStatus == that.installStatus && tipoCollegamento == that.tipoCollegamento && Objects.equals(serialNumber, that.serialNumber);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(modelId);
        result = 31 * result + Objects.hashCode(categoria);
        result = 31 * result + Objects.hashCode(sysClassName);
        result = 31 * result + Objects.hashCode(assetTag);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(marca);
        result = 31 * result + Objects.hashCode(modello);
        result = 31 * result + Objects.hashCode(location);
        result = 31 * result + Objects.hashCode(latitudine);
        result = 31 * result + Objects.hashCode(longitudine);
        result = 31 * result + Objects.hashCode(ipAddress);
        result = 31 * result + Objects.hashCode(parentalNode);
        result = 31 * result + Objects.hashCode(installStatus);
        result = 31 * result + Objects.hashCode(tipoCollegamento);
        result = 31 * result + Objects.hashCode(serialNumber);
        return result;
    }
}