package dk.openesdh.addo;

import org.json.JSONObject;

import net.vismaaddo.Weather;
import net.vismaaddo.WeatherReturn;
import net.vismaaddo.WeatherSoap;

public class AddoWebService {

    private WeatherSoap serviceSoap = null;

    public static void main(String[] args) {

        AddoWebService webService = new AddoWebService();

        System.out.println("Current weather in NY: ");
        System.out.println(webService.getCityWeather("10001"));
    }

    private WeatherSoap getService() {
        if (serviceSoap == null) {
            serviceSoap = new Weather().getWeatherSoap();
        }
        return serviceSoap;
    }

    public String getCityWeather(String zipCode) {
        zipCode = zipCode == null || zipCode.isEmpty() ? "10001" : zipCode;
        WeatherReturn cityWeatherByZIP = getService().getCityWeatherByZIP(zipCode);
        return new JSONObject(cityWeatherByZIP).toString();
    }

}
