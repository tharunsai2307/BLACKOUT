import React, { useEffect, useRef, useState, useCallback } from 'react';
import maplibregl, { Map as MapLibreMap, Marker, Popup, NavigationControl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { EmergencyAlert } from '../../types/database';
import { formatCoordinates } from '../../utils/formatters';
import { geoapifyService, GeocodeSearchResult } from '../../services/geoapifyService';
import { Search, MapPin, X, Loader2, Compass } from 'lucide-react';

interface EmergencyMapProps {
  incidents: EmergencyAlert[];
  selectedIncident?: EmergencyAlert | null;
  onSelectIncident?: (incident: EmergencyAlert) => void;
  className?: string;
  enableSearch?: boolean;
}

// India-wide Operational Center and Geographic Boundaries
const INDIA_CENTER: [number, number] = [78.9629, 20.5937];
const INDIA_ZOOM = 4.5;
const INDIA_BOUNDS: [[number, number], [number, number]] = [
  [68.1, 6.5],   // Southwest coordinates (Arabian Sea / Kerala)
  [97.4, 37.1],  // Northeast coordinates (Arunachal Pradesh / Ladakh)
];

export const EmergencyMap: React.FC<EmergencyMapProps> = ({
  incidents,
  selectedIncident,
  onSelectIncident,
  className = 'h-full w-full',
  enableSearch = true,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const selectedMarkerRef = useRef<Marker | null>(null);
  const searchMarkerRef = useRef<Marker | null>(null);
  const popupRef = useRef<Popup | null>(null);

  // Forward Geocoding Search State
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<GeocodeSearchResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [isMapLoaded, setIsMapLoaded] = useState(false);

  // Filter incidents with valid GPS coordinates (Do NOT render 0,0 or null on map)
  const validIncidents = incidents.filter(
    (inc) =>
      inc.latitude != null &&
      inc.longitude != null &&
      !(inc.latitude === 0 && inc.longitude === 0)
  );

  // Default OSM Style Specification
  const osmStyle: maplibregl.StyleSpecification = {
    version: 8,
    sources: {
      'osm-tiles': {
        type: 'raster',
        tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
        tileSize: 256,
        attribution: '&copy; OpenStreetMap contributors | BLACKOUT OPS',
      },
    },
    layers: [
      {
        id: 'osm-tiles-layer',
        type: 'raster',
        source: 'osm-tiles',
        minzoom: 0,
        maxzoom: 19,
      },
    ],
  };

  // Convert valid incidents to GeoJSON FeatureCollection
  const buildGeoJson = useCallback((): GeoJSON.FeatureCollection<GeoJSON.Point> => {
    return {
      type: 'FeatureCollection',
      features: validIncidents.map((incident) => ({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [incident.longitude!, incident.latitude!],
        },
        properties: {
          id: incident.id,
          message_id: incident.message_id,
          category: incident.category,
          priority: incident.priority,
          description: incident.description,
          created_at: incident.created_at,
          verification_status: incident.verification_status,
          hop_count: incident.hop_count,
          gateway_device_id: incident.gateway_device_id,
          original_sender_device_id: incident.original_sender_device_id,
        },
      })),
    };
  }, [validIncidents]);

  // Reset to National India Overview
  const resetToIndiaView = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;
    map.fitBounds(INDIA_BOUNDS, { padding: 40, maxZoom: 5.5, duration: 1000 });
  }, []);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return;

    const geoapifyStyleUrl = geoapifyService.getTacticalMapStyleUrl();
    const initialStyle = geoapifyStyleUrl || osmStyle;

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: initialStyle,
      center: INDIA_CENTER,
      zoom: INDIA_ZOOM,
      maxBounds: [
        [55.0, 4.0],   // Southwest boundary limit
        [110.0, 40.0], // Northeast boundary limit
      ],
    });

    map.on('error', (e) => {
      if (geoapifyStyleUrl && e.error?.message?.includes('style')) {
        console.warn('Geoapify style failed to load; falling back to OpenStreetMap tiles.');
        map.setStyle(osmStyle);
      }
    });

    map.addControl(new NavigationControl({ showCompass: true }), 'top-right');

    map.on('load', () => {
      setIsMapLoaded(true);
      setupClusterLayers(map);
    });

    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
      setIsMapLoaded(false);
    };
  }, []);

  // Setup MapLibre clustering source and layers
  const setupClusterLayers = (map: MapLibreMap) => {
    if (map.getSource('incident-clusters')) return;

    const geojsonData = buildGeoJson();

    map.addSource('incident-clusters', {
      type: 'geojson',
      data: geojsonData,
      cluster: true,
      clusterMaxZoom: 14,
      clusterRadius: 50,
    });

    // 1. Cluster circles with dynamic size and color coding
    map.addLayer({
      id: 'clusters',
      type: 'circle',
      source: 'incident-clusters',
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': [
          'step',
          ['get', 'point_count'],
          '#06B6D4', // cyan (< 5)
          5,
          '#F59E0B', // amber (5-15)
          15,
          '#EF4444', // red (>= 15)
        ],
        'circle-radius': [
          'step',
          ['get', 'point_count'],
          18,
          5,
          24,
          15,
          32,
        ],
        'circle-stroke-width': 2,
        'circle-stroke-color': '#0F172A',
        'circle-opacity': 0.85,
      },
    });

    // 2. Cluster count label
    map.addLayer({
      id: 'cluster-count',
      type: 'symbol',
      source: 'incident-clusters',
      filter: ['has', 'point_count'],
      layout: {
        'text-field': '{point_count_abbreviated}',
        'text-size': 12,
      },
      paint: {
        'text-color': '#FFFFFF',
      },
    });

    // 3. Pulse halo for critical unclustered points
    map.addLayer({
      id: 'unclustered-pulse',
      type: 'circle',
      source: 'incident-clusters',
      filter: ['all', ['!', ['has', 'point_count']], ['==', ['get', 'priority'], 'CRITICAL']],
      paint: {
        'circle-radius': 14,
        'circle-color': '#EF4444',
        'circle-opacity': 0.4,
        'circle-stroke-width': 1,
        'circle-stroke-color': '#EF4444',
      },
    });

    // 4. Unclustered individual points
    map.addLayer({
      id: 'unclustered-point',
      type: 'circle',
      source: 'incident-clusters',
      filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-color': [
          'match',
          ['get', 'priority'],
          'CRITICAL', '#EF4444',
          'HIGH', '#F59E0B',
          'MODERATE', '#06B6D4',
          '#3B82F6'
        ],
        'circle-radius': 7,
        'circle-stroke-width': 2,
        'circle-stroke-color': '#0F172A',
      },
    });

    // Expand cluster on click
    map.on('click', 'clusters', (e) => {
      const features = map.queryRenderedFeatures(e.point, { layers: ['clusters'] });
      if (!features.length) return;
      const clusterId = features[0].properties?.cluster_id;
      const source: any = map.getSource('incident-clusters');
      if (source && clusterId != null) {
        source.getClusterExpansionZoom(clusterId, (err: any, zoom: number) => {
          if (err) return;
          map.easeTo({
            center: (features[0].geometry as any).coordinates,
            zoom: zoom + 0.5,
            duration: 500,
          });
        });
      }
    });

    // Handle individual incident point click
    map.on('click', 'unclustered-point', (e) => {
      if (!e.features || !e.features.length) return;
      const f = e.features[0];
      const coords = (f.geometry as any).coordinates.slice();
      const props = f.properties || {};

      const markerColor = props.priority === 'CRITICAL' ? '#EF4444' : props.priority === 'HIGH' ? '#F59E0B' : '#06B6D4';

      const popupHtml = `
        <div style="color: #0F172A; font-family: monospace; padding: 4px; font-size: 11px; min-width: 180px;">
          <div style="font-weight: bold; color: ${markerColor}; margin-bottom: 2px;">
            [${props.priority}] ${props.category}
          </div>
          <div style="margin-bottom: 4px; color: #334155;">
            ${(props.description || '').substring(0, 80)}${(props.description || '').length > 80 ? '...' : ''}
          </div>
          <div style="font-size: 9px; color: #64748B; margin-bottom: 4px;">
            ${formatCoordinates(coords[1], coords[0])}
          </div>
          <div style="display: flex; justify-content: space-between; font-size: 9px; border-top: 1px solid #E2E8F0; padding-top: 4px;">
            <span>Hops: ${props.hop_count || 0}</span>
            <span>Status: <strong>${props.verification_status || 'UNVERIFIED'}</strong></span>
          </div>
        </div>
      `;

      if (popupRef.current) {
        popupRef.current.remove();
      }

      popupRef.current = new Popup({ offset: 12, closeButton: false })
        .setLngLat(coords)
        .setHTML(popupHtml)
        .addTo(map);

      // Notify parent selection
      const matchedIncident = incidents.find((i) => i.message_id === props.message_id);
      if (matchedIncident && onSelectIncident) {
        onSelectIncident(matchedIncident);
      }
    });

    // Pointer cursor styling
    map.on('mouseenter', 'clusters', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'clusters', () => { map.getCanvas().style.cursor = ''; });
    map.on('mouseenter', 'unclustered-point', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'unclustered-point', () => { map.getCanvas().style.cursor = ''; });
  };

  // Sync incidents with GeoJSON source and auto fitBounds
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !isMapLoaded) return;

    const source: any = map.getSource('incident-clusters');
    if (source) {
      source.setData(buildGeoJson());
    } else {
      setupClusterLayers(map);
    }

    // Auto-fit bounds if incidents exist and none is currently singled out
    if (!selectedIncident) {
      if (validIncidents.length > 0) {
        const bounds = new maplibregl.LngLatBounds();
        validIncidents.forEach((incident) => {
          bounds.extend([incident.longitude!, incident.latitude!]);
        });
        map.fitBounds(bounds, { padding: 60, maxZoom: 13, duration: 800 });
      } else {
        // Fall back to India-wide overview when list is empty
        map.fitBounds(INDIA_BOUNDS, { padding: 40, maxZoom: 5.5, duration: 800 });
      }
    }
  }, [validIncidents, isMapLoaded, buildGeoJson]);

  // Singled-out selected incident highlight marker
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !selectedIncident) {
      if (selectedMarkerRef.current) {
        selectedMarkerRef.current.remove();
        selectedMarkerRef.current = null;
      }
      return;
    }

    if (
      selectedIncident.latitude != null &&
      selectedIncident.longitude != null &&
      !(selectedIncident.latitude === 0 && selectedIncident.longitude === 0)
    ) {
      if (selectedMarkerRef.current) {
        selectedMarkerRef.current.remove();
      }

      const el = document.createElement('div');
      el.className = 'w-8 h-8 rounded-full border-2 border-white shadow-2xl flex items-center justify-center font-bold text-xs bg-operations-cyan text-slate-900 animate-pulse';
      el.innerHTML = '🎯';

      selectedMarkerRef.current = new Marker({ element: el })
        .setLngLat([selectedIncident.longitude, selectedIncident.latitude])
        .addTo(map);

      map.flyTo({
        center: [selectedIncident.longitude, selectedIncident.latitude],
        zoom: 14,
        essential: true,
      });
    }
  }, [selectedIncident]);

  // Handle Forward Geocoding Search
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;

    try {
      setIsSearching(true);
      const results = await geoapifyService.searchPlaces(searchQuery);
      setSearchResults(results);
      setShowDropdown(true);

      if (results.length > 0) {
        selectSearchResult(results[0]);
      }
    } finally {
      setIsSearching(false);
    }
  };

  const selectSearchResult = (result: GeocodeSearchResult) => {
    const map = mapRef.current;
    if (!map) return;

    setShowDropdown(false);
    setSearchQuery(result.formatted);

    if (searchMarkerRef.current) {
      searchMarkerRef.current.remove();
      searchMarkerRef.current = null;
    }

    const el = document.createElement('div');
    el.className = 'w-7 h-7 rounded-full bg-operations-cyan border-2 border-white shadow-xl flex items-center justify-center text-slate-900 font-bold font-mono text-xs animate-bounce';
    el.innerHTML = '⌖';

    const marker = new Marker({ element: el })
      .setLngLat([result.longitude, result.latitude])
      .addTo(map);

    searchMarkerRef.current = marker;

    map.flyTo({
      center: [result.longitude, result.latitude],
      zoom: 13,
      essential: true,
    });
  };

  const clearSearch = () => {
    setSearchQuery('');
    setSearchResults([]);
    setShowDropdown(false);
    if (searchMarkerRef.current) {
      searchMarkerRef.current.remove();
      searchMarkerRef.current = null;
    }
  };

  const noGpsCount = incidents.length - validIncidents.length;

  return (
    <div className={`relative overflow-hidden rounded border border-operations-border bg-operations-surface ${className}`}>
      <div ref={mapContainerRef} className="h-full w-full" />

      {/* Floating Forward Geocoding Search Bar */}
      {enableSearch && (
        <div className="absolute top-3 left-3 z-10 w-72 max-w-[calc(100%-4rem)] font-mono text-xs">
          <form onSubmit={handleSearch} className="relative">
            <input
              type="text"
              placeholder="Search address or landmark..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-operations-card/95 backdrop-blur border border-operations-border rounded pl-8 pr-7 py-1.5 text-white placeholder-slate-400 focus:outline-none focus:border-operations-cyan shadow-lg text-[11px]"
            />
            <Search className="w-3.5 h-3.5 text-operations-muted absolute left-2.5 top-2.5 pointer-events-none" />

            {isSearching ? (
              <Loader2 className="w-3.5 h-3.5 text-operations-cyan animate-spin absolute right-2.5 top-2.5" />
            ) : searchQuery ? (
              <button
                type="button"
                onClick={clearSearch}
                className="absolute right-2.5 top-2.5 text-operations-muted hover:text-white"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            ) : null}
          </form>

          {/* Search Dropdown Results */}
          {showDropdown && searchResults.length > 0 && (
            <div className="mt-1 bg-operations-card border border-operations-border rounded shadow-2xl max-h-48 overflow-y-auto divide-y divide-operations-border/60">
              {searchResults.map((res, idx) => (
                <div
                  key={idx}
                  onClick={() => selectSearchResult(res)}
                  className="p-2 hover:bg-operations-surface cursor-pointer text-[10px] text-slate-200 flex items-start space-x-1.5"
                >
                  <MapPin className="w-3 h-3 text-operations-cyan mt-0.5 flex-shrink-0" />
                  <span className="truncate">{res.formatted}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Quick Reset to India View Button */}
      <button
        onClick={resetToIndiaView}
        title="Reset map view to India National Overview"
        className="absolute top-3 right-14 z-10 bg-operations-card/90 hover:bg-operations-card border border-operations-border text-white text-[10px] font-mono px-2.5 py-1.5 rounded shadow-lg flex items-center space-x-1.5 transition-colors"
      >
        <Compass className="w-3.5 h-3.5 text-operations-cyan" />
        <span className="hidden sm:inline">India View</span>
      </button>

      {/* Floating GPS Coverage Info Badge */}
      <div className="absolute bottom-3 left-3 bg-operations-card/90 backdrop-blur border border-operations-border px-3 py-1.5 rounded text-[11px] font-mono text-operations-muted z-10 flex items-center space-x-3 shadow-lg">
        <span className="text-white font-bold">
          Mapped: <span className="text-operations-cyan">{validIncidents.length}</span> / {incidents.length}
        </span>
        {noGpsCount > 0 && (
          <span className="text-operations-amber">
            ({noGpsCount} alerts lacking GPS fix)
          </span>
        )}
      </div>
    </div>
  );
};
